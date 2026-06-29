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

Notes:

- A `CHECK` form that can't be safely satisfied (negation, `OR`, `LIKE` patterns, a one-sided
  bound) is **skipped with a warning**, and the column falls back to its type default.
- A per-column override or a [registry](./GENERATORS.md#custom-generator-registry) rule always
  wins, so you can still take full control of a constrained column.
- Open the connection with `stringtype=unspecified` (already required for PostgreSQL's extension
  types) so enum/`IN` values bind. Constraint reading is PostgreSQL-only today.

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
seed produce byte-for-byte the same data as a sequential fill.

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

Partitioning is reproducible **for a given configuration, including the partition count**: key
columns and the columns correlated with them (foreign keys, sequences, permutations) are generated
positionally, so they are byte-for-byte identical to a sequential fill and **foreign-key validity
always holds**. Only plain non-key random columns take different (but still deterministic) values
when you change the partition count — they carry no cross-row contract, so this is by design and
keeps the default path free of any per-cell cost.

Size the connection pool for the total concurrent demand (`threads`, where a partitioned table
counts as `partitions` units). One case is unsupported: partitioning a **parent** table whose
primary key is a plain *random* generator referenced by a foreign key can orphan those references —
partition the child table instead, or use the positional key generators (as the bundled
TPC-C/TPC-H configurations do). A custom generator with internal positional state must implement
`io.bloviate.gen.IndexedDataGenerator` to stay aligned under partitioning.

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
correctness — the result is byte-for-byte identical to an ordered fill of the same seed — it only
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
`DatabaseFiller.Builder` via `threads(n)` with the `DataSource` constructor.

### File generation options

- **Output Format**: CSV, TSV, or pipe-delimited
- **Row Count**: Number of rows to generate
- **Custom Column Definitions**: Full control over data generation
