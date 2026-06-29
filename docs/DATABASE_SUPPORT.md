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

> **PostgreSQL connection requirement:** the vendor types above are bound as their text
> representations, and PostgreSQL won't implicitly cast `varchar` to `uuid`/`jsonb`/`bit`/etc.
> Open the connection with `stringtype=unspecified` so the server infers each column's type:
> `jdbc:postgresql://host/db?stringtype=unspecified`.

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
