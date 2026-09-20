# Database Support

Bloviate resolves cross-database defaults for the common JDBC types and adds handling for
vendor-specific types on the databases that need it. Pick a `DatabaseSupport` implementation
explicitly, or let Bloviate detect it from the connection.

| Database | Support Class | Coverage |
|----------|---------------|----------|
| PostgreSQL | `PostgresSupport` | Standard JDBC types **plus** PostgreSQL vendor types |
| CockroachDB | `CockroachDBSupport` | Extends `PostgresSupport` (CockroachDB is wire-compatible) |
| MySQL | `MySQLSupport` | Standard JDBC types **plus** `JSON` |
| MariaDB | `MariaDBSupport` | Extends `MySQLSupport` (MariaDB speaks the MySQL wire protocol) |
| H2 | `H2Support` | Standard JDBC types **plus** `UUID` and `JSON` (embedded; no Docker) |
| SQLite | `SQLiteSupport` | Standard JDBC types via type affinity (embedded; no Docker) |
| BigQuery | `BigQuerySupport` | All scalar types **incl.** `JSON`, `GEOGRAPHY`, `INTERVAL`, `DATETIME`; no composites |
| Generic JDBC | `DefaultSupport` | Standard JDBC types only |

All of them resolve the cross-database defaults for the common JDBC types (integers, decimals,
strings, dates/times, booleans, binary, …). `PostgresSupport`, `MySQLSupport`, and `H2Support` add
handling for vendor-specific types on top of those defaults.

## Vendor types

**PostgreSQL vendor types:** `uuid`, `json`, `jsonb`, `inet`, `cidr`, `macaddr`, `macaddr8`,
`interval`, `bit`/`bit varying`, `xml`, and `text`/`integer`/`bigint` arrays.

**MySQL vendor types:** `JSON` (generated as valid JSON rather than arbitrary text). `ENUM`, `SET`,
`GEOMETRY`, and `YEAR` are **not** supported — they need value-aware or binary generation that
standard JDBC metadata doesn't expose.

**MariaDB:** extends `MySQLSupport`, so standard types fill with no configuration and
`TINYINT UNSIGNED` uses the inherited 0–255 generator. MariaDB's `JSON` type, however, is an alias
for `LONGTEXT` and the driver reports it through JDBC as `LONGTEXT` (not a distinct `JSON` type),
so Bloviate **cannot auto-detect it**. Because MariaDB also adds an automatic `CHECK (json_valid(…))`,
supply a per-column `JsonbGenerator` override (via `ColumnConfiguration`) for any `JSON` column.

**H2 vendor types:** `UUID` (the driver reports it as 16-byte `BINARY`; a real UUID is generated)
and `JSON` (generated as valid JSON). `ARRAY`, `INTERVAL`, `ENUM`, and `GEOMETRY` are **not** yet
supported. Note H2's `TINYINT` is signed (max 127), unlike MySQL's unsigned default.

**SQLite:** SQLite uses dynamic typing with column *affinity*, so declared types collapse — through
the JDBC metadata Bloviate reads — onto `INTEGER` / `FLOAT` / `VARCHAR`. There are no native
`BOOLEAN`/`DATE`/`DATETIME` types: booleans are filled as integers and dates/timestamps as text,
per SQLite convention, and every value round-trips through affinity rules. Foreign keys are off by
default (`PRAGMA foreign_keys = ON` enables them), but Bloviate orders fills by the foreign-key
graph regardless.

## BigQuery

Requires the [tbc-bq-jdbc](https://github.com/Two-Bear-Capital/tbc-bq-jdbc) driver
(`vc.tbc:tbc-bq-jdbc`, 4.3.0 or later), which is not yet on Maven Central — install it locally with
`./mvnw clean install` in that repo, or use its GitHub Releases jar. Version 4.4.0 is strongly
recommended once it is released, for the reason given under [server-constructed
values](#server-constructed-values).

```java
String url = "jdbc:bigquery:my-project/my_dataset?authType=ADC";
```

**Supported:** every scalar type — `STRING`, `BYTES`, `INT64`, `FLOAT64`, `NUMERIC`, `BIGNUMERIC`,
`BOOL`, `DATE`, `TIME`, `TIMESTAMP`, `DATETIME`, `JSON`, `GEOGRAPHY`, `INTERVAL`.

**Not supported:** the composite types — `ARRAY`, `STRUCT`, `RANGE`. These fail fast with a message
naming the column, before any rows are written. `ARRAY` and `STRUCT` need a shape Bloviate has no
representation for, and `RANGE` would need two parameters for one column, which the engine's
one-parameter-per-column binding cannot express.

To fill one anyway, supply a per-column generator through `ColumnConfiguration` or
`GeneratorRegistry.registerColumnNamePattern`. The driver does implement `Connection.createArrayOf`
and `Connection.createStruct`, so a hand-written generator can write composites today.

### Server-constructed values

BigQuery's *write* surface is narrower than its read surface. The driver reads `JSON`, `GEOGRAPHY`
and `INTERVAL` back as `VARCHAR` and `DATETIME` as `TIMESTAMP`, but has no parameter binding that
produces any of them, and BigQuery will not implicitly coerce a `STRING` or `TIMESTAMP` parameter
into them.

Those four are therefore generated as text and turned into the column's type by the server:

| Type | Generated as | Written as |
|------|--------------|------------|
| `JSON` | a JSON object literal | `PARSE_JSON(?)` |
| `GEOGRAPHY` | a WKT point, `POINT(lon lat)` | `ST_GEOGFROMTEXT(?)` |
| `INTERVAL` | `Y-M D H:M:S` | `CAST(? AS INTERVAL)` |
| `DATETIME` | a timestamp | `CAST(? AS DATETIME)` (interpreted as UTC) |

This is why 4.4.0 matters. It is not required — these columns fill correctly on 4.3.0 — but earlier
versions collapse a JDBC batch into a multi-row `INSERT` only when the `VALUES` tuple is
placeholders-only, so a table with any of these columns falls back to **one query job per row**.
That is correct, but slow enough to matter and expensive on a large fill.

At the time of writing 4.4.0 is merged but unreleased, so `tbc-bq-jdbc.version` still pins 4.3.0.

Two further consequences:

- A table containing one of these columns never takes the driver's NDJSON load-job path, even above
  `batchLoadThreshold`. That path writes bound values directly and never sees the SQL, so it would
  drop the wrapping and store the wrong thing; the driver keeps such batches on DML deliberately.
- The mechanism is general, not BigQuery-specific: any generator can declare its own
  `DataGenerator.valueExpression()`, and `SqlExpressionGenerator` wraps an existing generator
  without subclassing it. A custom PostGIS generator can use the same seam.

`BIGNUMERIC` is *not* in that table. It has no parameter binding either, but unlike the four above
it does not need one: the driver binds every `BigDecimal` as `NUMERIC`, and a `NUMERIC`-range value
is always valid in a `BIGNUMERIC` column. Values are therefore clamped rather than constructed —
see below.

> `GeneratorRegistry.registerTypeName` is a poor fit here: it matches type names exactly, and
> BigQuery reports the raw `INFORMATION_SCHEMA` text (`string(20)`, `numeric(10, 2)`,
> `array<int64>`), so only unparameterized names ever match.

> Generators registered by column-name pattern — including everything `bloviate-datafaker`
> contributes — rank **above** the support's own type mapping. A `GEOGRAPHY` column whose name
> matches such a pattern will get that generator instead of the error above, and fail at insert
> time. Exclude those columns explicitly if you use pattern-based generators.

### Generated value sizes

BigQuery reports `COLUMN_SIZE` as the *type* maximum, not a declared width: a bare `STRING` reports
2,097,152 and a bare `BYTES` reports 10,485,760. Sizes are therefore clamped downward — to 256
characters and 128 bytes — while a declared `STRING(20)` is honored exactly.

Those caps are ceilings, not target lengths, and the generators impose their own limits underneath:
`SimpleStringGenerator` never exceeds 2000 characters and `ByteGenerator` never exceeds 25 bytes. So
the string clamp is a further ~8x reduction that you will observe, whereas the `BYTES` clamp sits
above the generator's own limit and does not currently change any generated value. It is stated as a
bound so the intent survives a change to that generator.

`BIGNUMERIC` is clamped for a different reason: it reports precision 76 and scale 38, but the driver
binds every `BigDecimal` as `NUMERIC`, so generated values are clamped to `NUMERIC`'s (38, 9). The
binding is what forces this, not the destination column — a `NUMERIC`-range value is always valid in
a `BIGNUMERIC` column.

That clamp is a deliberate limit rather than a gap to close. `BigDecimalGenerator` already caps
itself at 25 significant digits on every database, on the grounds that enormous precision is not
useful test data (CockroachDB reports 131,089), so generating true 76-digit `BIGNUMERIC` values
would contradict that. If you need them, supply a per-column generator.

### Required driver settings

Both are the driver's defaults; overriding either breaks the fill.

- **`includeStructFields=false`** — with struct fields spliced in, `getColumns` reports dotted
  sub-field rows alongside their parent and the generated `INSERT` is structurally invalid.
- **`metadataLazyLoad=false`** — with lazy metadata, an unfiltered `getTables`/`getColumns` returns
  nothing and the fill silently does no work.

### Keys and fill order

BigQuery accepts `PRIMARY KEY`/`FOREIGN KEY` only as `NOT ENFORCED`, but the driver still surfaces
them through `getPrimaryKeys`/`getImportedKeys`, so Bloviate orders fills by the foreign-key graph
and aligns child values with their parents exactly as it does elsewhere. Declare keys in your DDL if
you want referentially consistent data; without them the graph has no edges and foreign-key columns
get independent random values.

Because nothing is ever enforced, `UNORDERED_BULK` is free here — there is no enforcement to suspend
and no ordering requirement, so `BigQuerySupport` enables it with no-op constraint handling.

### Performance and cost

Every statement is a BigQuery job, so per-row inserts are prohibitively slow and batching is
mandatory. The driver collapses a JDBC batch into a single multi-row `INSERT` automatically (no URL
parameter needed), chunked to stay under BigQuery's 10,000-parameters-per-query limit — so the
effective rows per job is `10_000 / columnCount`, and the default `batchSize` of 128 leaves most of
that on the table. Start from `batchSize = 5000`.

Setting `batchLoadThreshold` to a matching value moves large batches onto the driver's NDJSON
load-job path, which avoids DML quotas and per-job query cost entirely. That path requires
auto-commit to stay on, which is why `BigQuerySupport` opts out of engine-managed transactions:
`setAutoCommit(false)` starts a BigQuery session and silently disables it. **Leave
`CommitStrategy` at its default** — each `executeBatch` is already one atomic job, so an explicit
strategy buys nothing and costs the load path. Bloviate logs a warning if you set one anyway.

The load path is also unavailable to any table holding a `JSON`, `GEOGRAPHY`, `INTERVAL` or
`DATETIME` column, for the reason given under [server-constructed
values](#server-constructed-values). Such tables still collapse into multi-row `INSERT` statements
on driver 4.4.0 and later; they simply stay on DML.

> Filling a BigQuery dataset writes real data to real storage and runs real jobs. Both cost money.

> **PostgreSQL connection requirement:** the vendor types above are bound as their text
> representations, and PostgreSQL won't implicitly cast `varchar` to `uuid`/`jsonb`/`bit`/etc.
> Open the connection with `stringtype=unspecified` so the server infers each column's type:
> `jdbc:postgresql://host/db?stringtype=unspecified`.

## Partitioned tables

| Database | Partitioned tables |
|----------|--------------------|
| PostgreSQL | Declarative partitioning (`RANGE`, `LIST`, `HASH`, multi-level) is filled **through the parent**: the driver reports it as `PARTITIONED TABLE`, `PostgresSupport` discovers it and excludes every partition (`pg_class.relispartition`, including intermediate partitions), and the database routes rows. Constrain the partition key with a `ColumnConfiguration`; see [Partitioned tables](CONFIGURATION.md#partitioned-tables). |
| CockroachDB, MySQL, MariaDB, H2, SQLite, BigQuery | Unchanged. Their partitions are not exposed as separate tables through JDBC, so a partitioned table is discovered as one ordinary table. |
| Generic JDBC (`DefaultSupport`) | Unchanged: only `TABLE` is discovered. |

The behaviour is two hooks on `DatabaseSupport`, both opt-in and defaulting to today's behaviour:
`discoveredTableTypes()` (the JDBC table types to discover; `TABLE` by default, plus `PARTITIONED TABLE`
for `PostgresSupport`) and `readPartitions(connection, schema)` (which discovered tables are partitions,
each mapped to its top-level partitioned table; empty by default). A custom support for another database
can override them. `CockroachDBSupport` extends `PostgresSupport` but restores both defaults.

The `partitions` setting of `TableConfiguration` (intra-table parallelism, see
[Configuration](CONFIGURATION.md#intra-table-partitioning)) is unrelated to SQL partitioning.

## Auto-detection

You can let Bloviate pick the support implementation from the connection's metadata instead of
hardcoding it:

```java
DatabaseSupport support = DatabaseSupport.forConnection(connection);
```

> **Note:** CockroachDB is reached through the PostgreSQL JDBC driver and reports its product name
> as `PostgreSQL`, so auto-selection resolves it to `PostgresSupport`. Because `CockroachDBSupport`
> extends `PostgresSupport` and adds no extra behavior, the two are equivalent for data generation.

> **MariaDB note:** the MariaDB Connector/J driver reports `MariaDB`, which resolves to
> `MariaDBSupport`. The legacy MySQL Connector/J driver reports `MySQL` even against a MariaDB
> server and resolves to `MySQLSupport`; since `MariaDBSupport` adds no divergent behavior, the two
> are equivalent.

> **BigQuery note:** `BigQuerySupport` matches any product name containing `bigquery`, which covers
> both tbc-bq-jdbc (`BigQuery (TBC Driver)`) and Simba (`Google BigQuery`). It is written against
> tbc-bq-jdbc's metadata, though, and discriminates on the raw `INFORMATION_SCHEMA` type text, so
> some columns may not resolve under Simba's driver.
