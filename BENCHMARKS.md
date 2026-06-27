# Bloviate Benchmarks

Performance baseline for [issue #447](https://github.com/timveil/bloviate/issues/447)
("performance optimizations for large datasets"). These benchmarks exist so that the
optimizations tracked in that issue — parallel table fill, transaction/commit tuning,
batch-rewrite, and hot-loop micro-opts — can be measured against a trustworthy "before",
and so regressions are caught.

Everything lives in the **`bloviate-benchmarks`** module. It is never published (deploy,
install, source, javadoc and JaCoCo are all skipped) and nothing in `bloviate-core` depends
on it.

There are two complementary suites, because the work being optimized spans two regimes:

| Suite | What it measures | Where the wins show up | Tool |
|-------|------------------|------------------------|------|
| **CPU micro-benchmarks** | raw value generation and per-cell generator dispatch, no database | hot-loop micro-opts, generator-level changes | JMH |
| **End-to-end fill** | `DatabaseFiller.fill()` throughput (rows/sec) against a real DB | parallel table fill, commit tuning, batch rewrite | plain JUnit runner |

## CPU micro-benchmarks (JMH)

Pure-CPU, no Docker. Resolve generators exactly the way `TableFiller` does
(`DatabaseSupport.getDataGenerator(column, random)`), so the numbers reflect real engine cost.

- `GeneratorBenchmark` — throughput of `generate()` / `generateAsString()` across a spread of
  column types (int, numeric, varchar, timestamp, uuid, jsonb, …).
- `RowDispatchBenchmark` — models the inner loop of `TableFiller.fill()`: the per-cell
  `generatorMap.get(column)` HashMap lookup plus `generate()` over a wide row. This is the
  baseline for the planned "index generators by array position" change.

Build the runnable uber-jar and run it:

```bash
./mvnw -q -DskipTests -pl bloviate-benchmarks -am package
java -jar bloviate-benchmarks/target/benchmarks.jar
```

Useful invocations (standard JMH CLI — flags take a space):

```bash
# one benchmark, quick
java -jar bloviate-benchmarks/target/benchmarks.jar RowDispatchBenchmark

# restrict GeneratorBenchmark to specific column types
java -jar bloviate-benchmarks/target/benchmarks.jar GeneratorBenchmark.generate \
    -p genCase=UUID,VARCHAR_SHORT

# fewer forks/iterations while iterating locally
java -jar bloviate-benchmarks/target/benchmarks.jar -f 1 -wi 2 -i 3
```

## End-to-end fill (real database)

Opt-in JUnit runners tagged `@Tag("benchmark")`, one per database, reusing the same
TestContainers stack as the core tests. They are **skipped by a normal build** and only run
under the `bench` profile. Requires Docker (OrbStack works).

Each measured iteration truncates the schema, times a full `DatabaseFiller.fill()` (metadata
fetch included), and prints `rows/sec`. A fixed seed makes every iteration generate identical
data, so timings are comparable across runs and across optimization branches.

- `PostgresFillBenchmark` — TPC-C schema **and** a deliberately wide, FK-free schema
  (`create_wide.postgres.sql`); the wide one is the parallel-table-fill target.
- `MySqlFillBenchmark`, `CockroachFillBenchmark` — TPC-C schema.

```bash
# all end-to-end benchmarks (Postgres + MySQL + CockroachDB)
./mvnw -pl bloviate-benchmarks -am -Pbench test

# just Postgres, just the wide schema, larger dataset
./mvnw -pl bloviate-benchmarks -am -Pbench test \
    -Dtest='PostgresFillBenchmark#wide' -Dbench.rows=500000
```

Output lines look like:

```
[bench] postgres/wide    iteration 1        500,000 rows     3.612 s         138,430 rows/s
[bench] postgres/wide    best               500,000 rows     3.580 s         139,664 rows/s
[bench] postgres/wide    mean               500,000 rows     3.601 s         138,850 rows/s
```

### Tuning (system properties)

| Property | Default | Meaning |
|----------|--------:|---------|
| `bench.warmup` | 1 | untimed warmup fills (JIT / pool / cache priming) |
| `bench.iterations` | 3 | timed fills; best and mean are reported |
| `bench.batch` | 1000 | JDBC batch size (`DatabaseConfiguration.batchSize`) |
| `bench.rows` | 50000 | default row count for the **wide** schema (per table) |
| `bench.warehouses` | 1 | TPC-C scale factor (W) |
| `bench.items` | 10000 | TPC-C items (I) |
| `bench.districts` | 10 | TPC-C districts per warehouse (D) |
| `bench.customers` | 300 | TPC-C customers/orders per district (C) |
| `bench.minLines` / `bench.maxLines` | 5 / 15 | TPC-C order lines per order |

The defaults produce a modest (~60k-row TPC-C / ~500k-row wide) dataset that runs quickly;
scale them up for a real large-dataset measurement.

## Recording a baseline

Run on a quiet machine and record the environment alongside the numbers — JMH scores and
fill throughput are only comparable within the same hardware/JDK/DB image.

- Machine / CPU:
- JDK:
- Docker / DB image:

### CPU (JMH, ops/us — higher is better)

| Benchmark | Score | Notes |
|-----------|------:|-------|
| `RowDispatchBenchmark.dispatchRow` | | 16-column row |
| `GeneratorBenchmark.generate` (per `genCase`) | | |

### End-to-end (rows/sec — higher is better)

| Scenario | Rows | rows/sec (best) | Notes |
|----------|-----:|----------------:|-------|
| `postgres/tpcc` | | | |
| `postgres/wide` | | | |
| `mysql/tpcc` | | | |
| `cockroach/tpcc` | | | |
