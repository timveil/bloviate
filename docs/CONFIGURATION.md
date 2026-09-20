# Configuration

By default Bloviate inspects the schema and picks a generator for every column based on its JDBC
type. This guide shows how to take progressively more control — from overriding row counts, to
overriding individual columns, to non-uniform distributions, reproducible seeds, and the parallel
fill path.

Configuration is layered:

- **`DatabaseConfiguration`** — global defaults: batch size, default row count, database support,
  and an optional set of per-table overrides.
- **`TableConfiguration`** — overrides the row count for one table, and optionally carries
  per-column overrides.
- **`ColumnConfiguration`** — overrides how a single column is generated, via a
  `ColumnGeneratorFactory` (a `Random -> DataGenerator<?>` lambda). The engine hands the factory a
  column-seeded `Random` so output stays reproducible.

## Per-table row counts

Generate different numbers of rows for specific tables while every other table uses the default:

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;
import java.util.Set;

// "users" gets 50 rows; all other tables fall back to the default (100)
Set<TableConfiguration> tableConfigs = Set.of(
    new TableConfiguration("users", 50)
);

DatabaseConfiguration config = new DatabaseConfiguration(
    10,                    // batch size
    100,                   // default rows per table
    new PostgresSupport(), // database support
    tableConfigs           // table-specific overrides
);

new DatabaseFiller.Builder(connection, config)
    .build()
    .fill();
```

## Per-column generation overrides

Pin a specific column to a custom generator. Here the `status_code` column on `orders` is
constrained to integers in `[1, 10)` instead of the type's default:

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.IntegerGenerator;
import java.util.Set;

// ColumnGeneratorFactory is a `RandomGenerator -> DataGenerator<?>` lambda.
// The engine supplies a column-seeded RandomGenerator for reproducible output.
Set<ColumnConfiguration> columnConfigs = Set.of(
    new ColumnConfiguration("status_code",
        random -> new IntegerGenerator.Builder(random).start(1).end(10).build())
);

// 1,000 rows for "orders", with the column override applied
Set<TableConfiguration> tableConfigs = Set.of(
    new TableConfiguration("orders", 1000, columnConfigs)
);

DatabaseConfiguration config = new DatabaseConfiguration(
    128, 100, new PostgresSupport(), tableConfigs);

new DatabaseFiller.Builder(connection, config)
    .build()
    .fill();
```

Column names are matched **case-insensitively**. Any column without an override keeps its default,
type-based generator.

## Value distributions

Real columns are rarely uniform — a `status` is mostly `ACTIVE`, a `rating` clusters around its
mean, a referenced `product_id` follows a popularity curve, and a `created_at` bunches toward the
present. The `Distributions` helper returns ready-made `ColumnGeneratorFactory` values so a column
can opt into a **non-uniform distribution** without writing a factory:

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;
import java.util.Map;
import java.util.Set;

Set<ColumnConfiguration> columnConfigs = Set.of(
    // 70% NEW, 25% SHIPPED, 5% CANCELLED (weights need not sum to 1)
    new ColumnConfiguration("status",     Distributions.weighted(Map.of("NEW", 0.7, "SHIPPED", 0.25, "CANCELLED", 0.05))),
    // normal(mean=4, sd=1) rounded and clamped to [1, 5]
    new ColumnConfiguration("rating",     Distributions.normalInt(4, 1, 1, 5)),
    // Zipfian (power-law) over [1, 10000] — a few hot ids, a long thin tail
    new ColumnConfiguration("product_id", Distributions.zipfian(10_000)),
    // timestamps skewed toward the recent end of the window
    new ColumnConfiguration("created_at", Distributions.recentTimestamps())
);

DatabaseConfiguration config = new DatabaseConfiguration(
    128, 100, new PostgresSupport(),
    Set.of(new TableConfiguration("orders", 100_000, columnConfigs)));
```

Available shapes: `weighted(...)` (categorical), `normal(...)` / `normalInt(...)` (bounded
Gaussian), `zipfian(...)` (power-law), and `recentTimestamps(...)` (recency-skewed). Each is built
from the engine's column seed, so output stays **reproducible** and composes with foreign-key
reseeding and parallel fills like any other generator. These are *specified* distributions, not
distributions learned from real data.

## Constraint conformance

On **PostgreSQL**, Bloviate reads each table's `CHECK` constraints and `ENUM` types and generates
values that satisfy them — **automatically, no configuration**. So given:

```sql
CREATE TYPE order_status AS ENUM ('NEW', 'PAID', 'SHIPPED', 'CANCELLED');

CREATE TABLE orders (
    status   order_status NOT NULL,
    rating   integer       CHECK (rating BETWEEN 1 AND 5),
    priority integer       CHECK (priority IN (1, 2, 3)),
    amount   numeric(8,2)  CHECK (amount >= 0 AND amount <= 9999.99)
);
```

`status` only gets one of its enum labels, `rating` lands in `[1, 5]`, `priority` is one of
`1/2/3`, and `amount` stays in range — instead of random values an insert would reject. The common
forms are honored: `IN (...)`, `BETWEEN`, and `>=`/`<=`/`>`/`<` comparisons, for integer, numeric,
floating, and text columns, plus enum/domain allowed values.

Dates that must fall on the first of a period are honored too, on `DATE`, `TIMESTAMP` and
`TIMESTAMP WITH TIME ZONE` columns (since 3.5.0):

```sql
CREATE TABLE invoices (
    billing_month date CHECK (date_trunc('month', billing_month) = billing_month),
    period_start  timestamptz CHECK (EXTRACT(day FROM period_start) = 1)
);
```

`date_trunc('month' | 'quarter' | 'year', col) = col` and `EXTRACT(day FROM col) = 1` get a
[`TruncatedDateGenerator`](./GENERATORS.md#first-of-month-dates): the first day of a random month in
a fixed window (2015 up to 2025 by default; the same seed gives the same data), or midnight on that
day for a timestamp. A `timestamptz` value is midnight in the *session* time zone, which is the zone
the check is evaluated in, so the fill works whatever the connection's zone is.

Notes:

- The parser only recognises the exact forms above: a bare column, optionally cast, compared with
  literals. A `CHECK` that calls a function on the column (`lower(status) IN (...)`, `length(name) >= 1`),
  does arithmetic, or takes any other form is **skipped with a warning**, and the column falls back to
  its type default. That includes negation, `OR`, `LIKE` patterns, a one-sided bound, other
  `date_trunc` units (`day`, `week`, ...), and **`CHECK`s over more than one column**.
- Before 3.5.0 a quoted function argument was read as an allowed value, so
  `date_trunc('month', d) = d` made the fill fail with `invalid input syntax for type date: "month"`.
- A per-column override or a [registry](./GENERATORS.md#custom-generator-registry) rule always
  wins, so you can still take full control of a constrained column.
- Open the connection with `stringtype=unspecified` (already required for PostgreSQL's extension
  types) so enum/`IN` values bind. Constraint reading is PostgreSQL-only today: CockroachDB (and
  every other database) reads no `CHECK`s, so give those columns an explicit generator.

## Reproducible data with seeds

`DatabaseConfiguration` takes a base **seed**. The same schema filled with the same seed always
produces identical data, so test fixtures are deterministic; change the seed for a different — but
still reproducible — dataset. Per-column seeds are derived from stable column identity, and foreign
keys are seeded from their referenced primary key, so referential fidelity holds for any seed.

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;

// batch size, rows/table, support, table configs, seed
DatabaseConfiguration config = new DatabaseConfiguration(
    128, 100, new PostgresSupport(), null, 42L);

new DatabaseFiller.Builder(connection, config).build().fill();
```

The seed defaults to `0` when you use the four-argument constructor, so existing code keeps a
single, stable dataset without changes.

## Parallel table fill

For large, wide schemas the fill can run **in parallel**. Construct the filler from a pooled
`DataSource` instead of a single `Connection` and ask for more than one worker thread:

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;
import javax.sql.DataSource;

DataSource dataSource = /* a pooled DataSource, e.g. HikariCP */;

DatabaseConfiguration config = new DatabaseConfiguration(
    1000, 100_000, new PostgresSupport(), null, 42L);

new DatabaseFiller.Builder(dataSource, config)
    .threads(8)   // fill independent tables concurrently
    .build()
    .fill();
```

Bloviate groups tables into topological levels by their foreign keys and fills the independent
tables within each level concurrently, **one connection per worker**, barriering between levels so
a child table is never filled before its parent. Each worker fills its table in a single
transaction (commit once per table). The fill stays **fully reproducible**: a table's data depends
only on its own seed and row order, never on which tables fill alongside it, so the same config and
seed produce the same row content as a sequential fill across every deterministic column (physical
row order and wall-clock columns aside).

How much this helps depends on the schema. A wide schema of independent tables sees a large speedup
(~3× with 8 workers on a 10-table, 1M-row fixture); a deep, narrow foreign-key chain (each table
depending on the previous) has little to parallelize. See [the benchmarks](./BENCHMARKS.md) for
numbers.

The single-`Connection` constructor is unchanged and remains the default sequential path — `threads`
only applies to the `DataSource` form.

### Intra-table partitioning

When a **single large table dominates** the fill, between-table parallelism can't help it — it sits
alone in its topological level. Set `partitions` on that table's `TableConfiguration` to split its
rows into that many contiguous ranges filled concurrently, one connection per range, on the
parallel (`DataSource` + `threads`) path:

```java
// split the one big table into 8 row ranges; ignored on the single-Connection path
Set<TableConfiguration> tables = Set.of(
    new TableConfiguration("events", 50_000_000L, 8 /* partitions */));

DatabaseConfiguration config = new DatabaseConfiguration(
    1000, 0, new PostgresSupport(), tables, 42L);

new DatabaseFiller.Builder(dataSource, config).threads(8).build().fill();
```

Partitioning is **byte-identical to a sequential fill of the same seed, for any partition count**:
every built-in generator derives each value as a pure function of its column seed and the absolute
row index (per-index derivation), so keys, foreign keys, and plain random columns alike land on
exactly the values the sequential fill produces, **foreign-key validity always holds**, and seeking a
worker to its starting row is O(1) no matter how large the table or its parents are. The only
exceptions are custom generators that opt out of per-row positioning (`DataGenerator.positionable()`
returning false, as the datafaker integration does because its values come from an internal Faker
RNG) — those stay deterministic for a given partition count but may differ across partition counts.

Size the connection pool for the total concurrent demand (`threads`, where a partitioned table
counts as `partitions` units). One case is unsupported: partitioning a **parent** table whose
primary key comes from a non-positionable custom generator referenced by a foreign key can orphan
those references — partition the child table instead, or use the positional key generators (as the
bundled TPC-C/TPC-H configurations do). A custom generator with internal positional state must
implement `io.bloviate.gen.IndexedDataGenerator` to stay aligned under partitioning.

## Commit strategy

By default the engine leaves the connection's autocommit untouched (a typical autocommit connection
commits per `executeBatch()`). Disabling autocommit and committing less often cuts overhead. Pass a
`CommitStrategy` to `DatabaseConfiguration` for the sequential path:

```java
import io.bloviate.db.*;

// commit once per table (autocommit off for the fill, restored afterward)
DatabaseConfiguration perTable = new DatabaseConfiguration(
    1000, 100_000, new PostgresSupport(), null, 42L, CommitStrategy.perTable());

// or bound the open transaction: commit every 50 JDBC batches
DatabaseConfiguration everyN = new DatabaseConfiguration(
    1000, 100_000, new PostgresSupport(), null, 42L, CommitStrategy.everyNBatches(50));
```

The default, `CommitStrategy.connectionDefault()`, preserves today's behavior (the engine never
touches autocommit). The parallel path already commits once per table; a configured strategy
applies there too.

> **Tip — driver batch rewrite.** Bloviate inserts in JDBC batches, but most drivers only collapse
> a batch into a single multi-row `INSERT` when you opt in via the JDBC URL: PostgreSQL
> `reWriteBatchedInserts=true`, MySQL `rewriteBatchedStatements=true`. Enabling it is often the
> single biggest fill speedup, sequential or parallel. Bloviate **logs a warning** at fill time when
> the parameter is missing, and
> `io.bloviate.util.JdbcUrls.withBatchRewrite(url, support.batchRewriteUrlParameter())` builds a
> correctly-parameterized URL if you construct the `DataSource` yourself. CockroachDB ignores the
> parameter, so no warning is emitted there.

> **Tip — BigQuery.** Rewriting is unconditional in tbc-bq-jdbc, so there is no parameter to set and
> no warning to emit. Instead, size `batchSize` against BigQuery's limit of 10,000 query parameters
> per query: the effective rows per job is `10_000 / columnCount`, so the default 128 leaves most of
> a job unused. Start from `batchSize = 5000`, and set the driver's `batchLoadThreshold` to match to
> move large batches onto its NDJSON load-job path. Leave `CommitStrategy` at its default —
> `BigQuerySupport` opts out of engine-managed transactions because `setAutoCommit(false)` starts a
> BigQuery session and silently disables that load path, and each `executeBatch` is already one
> atomic job. Bloviate logs a warning if a commit strategy is configured anyway.

## Bulk load (unordered fill)

The parallel path normally barriers between topological levels, so a **deep, narrow foreign-key
chain** (each table depending on the previous) serializes — there is little within any one level to
run concurrently. `BulkLoadStrategy.unorderedBulk()` removes that barrier: it disables foreign-key
enforcement, fills **every** table at once, then re-enables enforcement.

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;

DatabaseConfiguration config = new DatabaseConfiguration(
    1000, 100_000, new PostgresSupport(), null, 42L,
    null,                              // CommitStrategy (null = default)
    BulkLoadStrategy.unorderedBulk()); // disable constraints, fill barrier-free, re-enable

new DatabaseFiller.Builder(dataSource, config).threads(8).build().fill();
```

This is safe because Bloviate's data is **referentially consistent by construction**: a foreign-key
column is seeded from its referenced primary-key column, so child and parent generate identical key
values regardless of insert order. Disabling enforcement therefore changes nothing about
correctness — for the same seed the result has the same row content as an ordered fill across every
deterministic column (physical row order aside) — it only
removes the ordering constraint and the per-row foreign-key checks. The win is largest on deep
chains (e.g. TPC-C's `warehouse → district → customer → open_order → order_line`); wide, FK-free
schemas already saturate their workers in one level and see little change.

Requirements and fallback:

- Only effective on the parallel path (a `DataSource` with `threads > 1`); it is ignored with a
  warning on the single-`Connection` and single-thread paths, which fill in dependency order.
- Supported on **PostgreSQL** (`SET session_replication_role = replica`, which needs a
  superuser/`rds_superuser` role) and **MySQL** (`SET FOREIGN_KEY_CHECKS=0`/`UNIQUE_CHECKS=0`, no
  special privilege). **CockroachDB** does not support it and transparently falls back to the
  ordered level-parallel path.
- Each worker disables enforcement on its own pooled connection and restores it in a `finally`
  before returning the connection to the pool, so no connection ever leaks back with checks
  suppressed. If enforcement cannot be disabled (e.g. the role lacks privilege), the engine logs a
  warning and falls back to the ordered path rather than running half-disabled.

The default, `BulkLoadStrategy.ordered()`, preserves today's behavior (dependency-ordered, constraints
always enforced).

## SQL hooks

`DatabaseFiller.Builder` can run SQL scripts around the fill: **`before`** hooks run before any table
is filled, **`after`** hooks run once every table is filled. The classic use is a derived table
computed from the generated rows (a rollup, a summary), so it cannot disagree with its source;
another is emptying tables so a fill can be re-run.

```java
import io.bloviate.db.*;
import java.nio.file.Path;
import java.util.Map;

new DatabaseFiller.Builder(connection, config)
    .before(SqlScript.resource("sql/reset.sql"))                       // classpath resource
    .after(SqlScript.file(Path.of("sql/rollup.sql"))                   // file on disk
        .withTokens(Map.of("schema", "reporting")))                    // ${schema} in the script
    .after(SqlScript.inline("analyze", "ANALYZE orders_by_day"))       // inline text
    .build()
    .fill();
```

```sql
-- sql/rollup.sql
DELETE FROM ${schema}.orders_by_day;
INSERT INTO ${schema}.orders_by_day (day, orders, revenue)
SELECT order_date, count(*), sum(total) FROM orders GROUP BY order_date;
```

`before(...)` and `after(...)` are additive and ordered: call them as often as you like and the
scripts run in the order added. A `SqlScript` is a name (used in log lines and error messages) plus
its text, from `SqlScript.inline(name, sql)`, `SqlScript.file(path)` or `SqlScript.resource(name)`.
`SqlScriptRunner.run(connection, script)` runs one on its own, for example to load a schema.

**What a script may contain.** Statements are separated by `;`. A `;` inside a `'string'` (with `''`
as the escape, and `\'` inside `E'...'`), a `"quoted"` or `` `backtick` `` identifier, a `--` or
`/* */` comment (which nests, as in PostgreSQL), or a PostgreSQL dollar-quoted body (`$$...$$`,
`$tag$...$tag$`, so `CREATE FUNCTION` bodies work) does not end a statement. Empty statements are
skipped and the last statement needs no `;`. `${name}` is replaced from the script's tokens,
everywhere except inside comments; a token with no value fails the script before any statement runs.
MySQL's `DELIMITER` command is **not supported** and fails with a clear message rather than
mis-splitting the script, and backslash escapes are honoured only in `E'...'` strings.

**Where hooks run.** Before hooks run first, ahead of the schema read, so they can create the tables
about to be filled. After hooks run last. On the single-`Connection` path both run on the connection
you supplied. On a `DataSource` without `threads(n)` (or with `threads(1)`) Bloviate borrows one
connection and uses it for the before hooks, the fill and the after hooks, so session state a hook
sets (`SET search_path`, a temporary table) carries through, and the after hooks see the fill's own
uncommitted rows even if the pool hands out `autoCommit=false` connections. With `threads(n)` greater
than 1 each phase borrows a connection, runs its hooks and returns it *before* the workers start or
after they have all finished, so a pool as small as `threads` cannot be starved; there, session state
a hook sets does not carry into the fill or into the other phase.

**Transactions.** Each script runs on the connection as it is; Bloviate never changes its
autocommit setting.

- *Autocommit on* (the default for most drivers and pools): each statement commits as it runs. A
  failure leaves the statements before it applied.
- *Autocommit off*: the script commits once, after its last statement succeeds, and rolls back if
  any statement fails. There is no separate transaction for the script: it runs in the connection's
  open transaction, so the commit or rollback applies to *everything* pending on that connection,
  not only the script's own statements. That includes work the caller had run and not yet committed
  and rows from a fill left uncommitted by `CommitStrategy.connectionDefault()`. A failing `before`
  or `after` script therefore rolls those back too, and a successful one commits them.
- Databases that commit DDL implicitly (MySQL, for one) commit it regardless of the mode.

**Failures.** Any failing statement fails `fill()` with a `SQLException` whose message names the
script and the statement's number, line and first line of text (for example
`SQL script [rollup.sql] failed at statement 2 (line 4) [INSERT INTO ...]: ...`); the driver's
exception is the cause and its SQL state is kept. A failing `before` hook stops the fill before any
table is written. A failing `after` hook is thrown, not swallowed. `after` hooks do not run if the
fill itself failed. There is no cross-table rollback: as with any failed fill, tables filled before
the failure stay filled, and a failing `after` hook does not undo the fill.

**A fill is not idempotent.** Running it again against tables that already hold its rows collides on
the primary keys. The supported way to re-run is a `before` hook that empties the tables first:

```java
.before(SqlScript.inline("reset", "TRUNCATE orders, customers CASCADE"))
```

**Derived tables.** The tables to fill are read after the before hooks and before the after hooks.
A table an after hook *creates* (`CREATE TABLE summary AS SELECT ...`) is therefore never filled. A
derived table that already exists is filled like any other, so **exclude it**
(`excludeTables("summary")`, see [Selecting tables and schema](#selecting-tables-and-schema)) and let
the after hook compute it; otherwise it is filled with random rows that the hook then has to delete.

## Selecting tables and schema

By default Bloviate fills every table (views are not filled) in the connection's current catalog and
schema. `DatabaseFiller.Builder` narrows that:

```java
new DatabaseFiller.Builder(connection, config)
    .schema("reporting")                          // fill this schema, not the connection's current one
    .includeTables("orders", "order_*")           // only these (default: all tables)
    .excludeTables("order_stats", "tmp_*")        // ...except these
    .after(SqlScript.inline("rollup", """
        INSERT INTO order_stats (day, orders, revenue)
        SELECT order_date, count(*), sum(total) FROM orders GROUP BY order_date"""))
    .build()
    .fill();
```

`includeTables` and `excludeTables` take varargs or a `Collection<String>`, and are additive across
calls.

**Patterns.** A pattern is an unqualified table name, matched case-insensitively (like
`TableConfiguration` names). `*` matches any run of characters (including none) and `?` matches exactly
one; every other character, including `.`, `[` and `%`, is literal, and there is no escape. Patterns
match table names *within the selected schema*; they are never schema-qualified.

**Rules.**

- With no `includeTables`, every table is a candidate; with it, only tables matching at least one
  include pattern are. `excludeTables` is applied after that, so an excluded table is out even if an
  include pattern also names it.
- An include pattern that matches no table fails `fill()` with an `IllegalArgumentException` naming
  the pattern and listing the tables found: an empty selection is nearly always a typo. So does a
  selection that leaves no table at all.
- An exclude pattern that matches no table only logs a warning, so an exclude list can outlive a
  dropped table.
- These checks run when the schema is read, after any `before` hooks, and before any row is written.

**Foreign keys to a table that is not filled.** Values in a foreign-key column are generated from the
parent's primary key, so a selected table cannot reference a table that is left out. `fill()` fails
with an `IllegalArgumentException` *before writing any row*, naming every offending child table, its
foreign-key column(s) and the missing parent:

```
cannot fill: table [orders] foreign key on column(s) [customer_id] references table [customers], which
is not among the tables being filled (left out by includeTables/excludeTables, or in another schema).
Add the referenced table(s) to includeTables (or remove the excludeTables pattern that drops them), or
exclude the referencing table(s) as well. Nothing was written.
```

Excluding a table that nothing references (a leaf, such as a derived rollup) always works, and a table
that references itself is not an excluded parent. A foreign key into another schema (or catalog) is
reported the same way, naming the parent's schema, even when the selected schema has a table of the
same name: the foreign key does not reference that one. A table in another schema cannot be included,
so exclude the referencing table.

**Table configurations that do not apply.** A `TableConfiguration` naming a table that does not exist
in the selected schema is still ignored, but `fill()` now logs one warning listing those names. A second
warning lists configurations for tables that exist in the schema but that `includeTables`/`excludeTables`
left out, since they have no effect.

**The derived-table pattern.** A rollup or summary table exists in the schema but must be computed from
the generated detail rows, not filled with random data. Exclude it, and populate it in an `after`
hook (as in the example above). Hooks run in the selected schema, so their unqualified names resolve
there. Because the hook reads the rows the fill wrote, the derived table always agrees with them.

**Schema and catalog selection.** `schema(...)` and `catalog(...)` call `Connection.setSchema` /
`setCatalog` on *every* connection the fill uses: the metadata read, each parallel worker, and the
hook phases. The previous values are put back afterwards:

- On a `Connection` you supply, when `fill()` returns or throws, so your connection is left as it was.
- On a `DataSource` connection, before it goes back to the pool (with `threads(1)` the single borrowed
  connection is scoped for the whole run; with `threads(n)` each worker's connection is scoped as it is
  borrowed). If a pooled connection cannot be restored it is aborted so the pool discards it.

The name is passed to the driver as given, so its case must match the database's (`"reporting"` and
`"REPORTING"` differ on PostgreSQL, and H2 folds unquoted names to upper case). Bloviate checks that the
connection reports the requested value after setting it, so a schema that does not exist, or a driver
that ignores the request, is a `SQLException` from `fill()` ("cannot select schema [x]...") rather than a
silent fill of the wrong schema. Databases differ:

- **PostgreSQL, H2, CockroachDB:** use `schema(...)`. PostgreSQL cannot change database on a connection,
  so `catalog(...)` there fails unless it names the current database.
- **MySQL, MariaDB:** a database is a catalog and there are no schemas, so use `catalog("db_name")`;
  `schema(...)` fails.
- **SQLite:** neither is supported; `schema(...)`/`catalog(...)` fail.

Two details worth knowing. PostgreSQL's driver implements `setSchema` by replacing the whole
`search_path` with the one schema, so inside the fill (hooks included) types and functions living in
other schemas, such as `public`, need qualifying, and the restore puts back the single schema the
connection reported, not a multi-entry `search_path`.

On a connection with **autocommit off**, PostgreSQL's `setSchema` is part of your open transaction, as
is anything you have pending. `fill()` leaves that transaction open and the connection back on its
original schema (`getSchema()` and `getAutoCommit()` are as you left them), so you can commit or roll
back afterwards. The selection is applied and restored for each phase (before hooks, the fill, after
hooks), and when a phase committed (hooks always do; the fill does with an engine-managed
[commit strategy](#commit-strategy)) the restore is committed as well, so a later `rollback()` cannot
put the connection back on the selected schema. With the default connection-default strategy the
fill's own rows stay uncommitted until you commit, and a `rollback()` discards them together with the
selection. If a fill fails in a way that aborts the transaction (a constraint violation, say) the
restore cannot run until you `rollback()`; the connection is then back on its original schema, and
you get the fill's own error, with the failed restore attached as a suppressed exception.

Selecting the connection's current schema explicitly changes nothing, including the generated
data: the seed depends on the table's real schema and catalog names, never on how they were chosen.

## Partitioned tables

On PostgreSQL, Bloviate fills a declaratively partitioned table (`PARTITION BY RANGE`, `LIST` or
`HASH`, at any depth) **through its parent**: it discovers the partitioned table, leaves every one of
its partitions out, and inserts rows into the parent so the database routes each one to the partition
that accepts it. A partition is a table in its own right that only accepts rows within its bounds, so
filling one directly with unconstrained values fails; that is why Bloviate never does.

```sql
CREATE TABLE orders (
    id bigint NOT NULL, tenant_id integer NOT NULL, placed_at timestamp NOT NULL, total numeric(10,2) NOT NULL,
    PRIMARY KEY (id, placed_at)
) PARTITION BY RANGE (placed_at);
CREATE TABLE orders_2024_01 PARTITION OF orders FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE orders_2024_02 PARTITION OF orders FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
```

Here Bloviate fills `orders`; `orders_2024_01` and `orders_2024_02` are not tables to fill. Keys,
foreign keys and constraints work on the parent as on any table: a primary key that includes the
partition key, a foreign key *from* a partitioned table to a plain table, and a foreign key from a plain
table *to* a partitioned table (PostgreSQL clones that key onto every partition; Bloviate reads it once,
against the parent).

**Constrain the partition key.** Generated values default to a window around 2020 for timestamps and
dates, and to arbitrary text or numbers otherwise, none of which is likely to fall in your partitions.
Give the partition key a `ColumnConfiguration` whose range the partitions cover:

```java
import io.bloviate.gen.SqlTimestampGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;

ColumnConfiguration placedAt = new ColumnConfiguration("placed_at", random ->
    new SqlTimestampGenerator.Builder(random)
        .start(Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 0, 0)))    // inclusive
        .end(Timestamp.valueOf(LocalDateTime.of(2024, 3, 1, 0, 0)))      // exclusive
        .build());

Set<TableConfiguration> tables = Set.of(new TableConfiguration("orders", 10_000, Set.of(placedAt)));
```

For a `LIST` key use a generator that only produces the listed values (for example
`Distributions.weighted(Map.of("eu", 1, "us", 1))`); a `HASH` key needs nothing, because every value hashes
to some partition. A column that references a partitioned table's key is generated by its *own* column's
generator, seeded from the parent's key, so give it the same configuration as the key it references
(here, `placed_at` of the referencing table).

**A `DEFAULT` partition.** If the table has one, a value that no other partition accepts lands there, so
an unconfigured fill succeeds, and everything ends up in the default partition. Configure the key as
above to spread rows over the named partitions.

**When a value fits no partition.** With no `DEFAULT` partition the insert fails, and `fill()` throws
the driver's `SQLException` for the failed batch, which names the table and the offending key:

```
Batch entry 0 insert into "shop"."orders" ("id","placed_at",...) values (...) was aborted:
ERROR: no partition of relation "orders" found for row
  Detail: Partition key of the failing row contains (placed_at) = (2020-03-19 09:46:32.763).
```

Nothing is skipped silently. Under the default `CommitStrategy.connectionDefault()` the engine leaves
your connection's autocommit alone, so what survives the failure is up to it: on an autocommit
connection, the batches already executed (earlier tables, and earlier batches of the failing one) stay
committed, as for any other failure; on a connection with autocommit off, they are still uncommitted and
you can `rollback()`. A [commit strategy](#commit-strategy) of `perTable()` rolls the failing table back.

**Selecting and configuring.** Use the partitioned table's name everywhere: `includeTables("orders")`,
`excludeTables("orders")` and `new TableConfiguration("orders", ...)`. Bloviate warns, and otherwise
ignores, a name that belongs to a partition:

- a `TableConfiguration` for `orders_2024_01` logs `table configuration for [orders_2024_01] is ignored:
  it is a partition of [orders] ... configure [orders] instead`;
- an `includeTables`/`excludeTables` pattern that matches only partitions logs a warning naming them and
  their parent (an include pattern that then leaves nothing to fill fails as usual). A wildcard such as
  `orders*` that matches the parent as well is fine: partitions are just never filled.

**Composition.** Partitioned tables work with everything else, verified against PostgreSQL: parallel
fills (`threads(n)`), `unorderedBulk()`, `schema(...)` and the table selection above. The intra-table
`partitions` setting of `TableConfiguration` (see [Intra-table partitioning](#intra-table-partitioning))
is a different thing that shares the word: it splits one table's *rows* across workers and has nothing to
do with SQL partitioning. The two combine: each worker inserts its row range through the partitioned parent, and
the rows equal those of a sequential fill.

**What is not supported.**

- Only PostgreSQL (with `PostgresSupport`) discovers partitions. On other databases nothing changes:
  MySQL, MariaDB, CockroachDB, H2, SQLite and BigQuery do not expose partitions as separate tables through
  JDBC, so a partitioned table is one table there (or, for CockroachDB, not SQL-partitioned at all). A
  PostgreSQL fill configured with `DefaultSupport` does not know about partitions, so it keeps the
  behaviour described in the note below.
- Legacy inheritance partitioning (`CREATE TABLE ... INHERITS`) is not declarative partitioning: those
  tables are filled as the ordinary tables they are.
- A foreign key that references **one partition directly** cannot be honoured, since a partition is never
  filled: Bloviate logs a warning and the fill fails, before writing anything, naming the referenced partition
  as a table that is not being filled. Reference the partitioned table instead. (A direct foreign key that
  exactly mirrors one to the parent, the same columns against the same-named column of a partition, cannot
  be told apart from the copies PostgreSQL makes and is treated as one.)
- Bloviate does not create partitions or choose a partition key range for you; an anchored, relative
  range for the key is planned separately.

> **Behaviour change (3.6.0).** Before partitioned tables were supported, Bloviate skipped the parent and
> filled each partition as an independent table with unconstrained values, which fails unless the
> partitions happen to accept them. A schema whose partitions do accept the default values (for example
> a table partitioned to cover 2020) used to fill leaf-by-leaf and now fills through the parent: the same
> seed produces different rows there. Schemas without partitioned tables are unaffected.

## Configuration options reference

### Database configuration options

- **Batch Size**: Number of records inserted in each batch operation
- **Record Count**: Default number of records to generate per table
- **Database Support**: Database-specific implementation for optimal compatibility
- **Table Configurations**: Override the row count for specific tables, and the intra-table
  `partitions` count for splitting a large table across workers on the parallel path
- **Column Configurations**: Override the generator for specific columns (case-insensitive,
  reproducible)
- **Seed**: Base seed for reproducible generation; the same schema and seed always produce the same
  data (defaults to `0`)
- **Commit Strategy**: How the engine commits — leave autocommit alone (default), commit once per
  table, or commit every N batches
- **Bulk Load Strategy**: Fill in foreign-key dependency order (default), or `unorderedBulk()` to
  disable constraint enforcement and fill every table at once with no topological barrier (parallel
  path only; PostgreSQL/MySQL, with CockroachDB falling back)

Parallelism (worker threads for concurrent table fill) is configured on the
`DatabaseFiller.Builder` via `threads(n)` with the `DataSource` constructor, and so are the
`before(...)`/`after(...)` [SQL hooks](#sql-hooks) and the `schema(...)`/`catalog(...)`/
`includeTables(...)`/`excludeTables(...)` [table and schema selection](#selecting-tables-and-schema).

### File generation options

- **Output Format**: CSV, TSV, or pipe-delimited
- **Row Count**: Number of rows to generate
- **Custom Column Definitions**: Full control over data generation
