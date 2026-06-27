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

Run on a quiet machine and record the environment alongside the numbers — JMH scores and fill
throughput are only comparable within the same hardware/JDK/DB image. Capture the machine/CPU, the
JDK, and the Docker/DB image next to the figures, exactly as the recorded results below do.

## Recorded results — issue #447 optimizations

Measured before/after the first round of [issue #447](https://github.com/timveil/bloviate/issues/447)
optimizations: the **hot-loop micro-opt** (positional generator dispatch in `TableFiller`) and the
**parallel table fill** (`DataSource` + `threads`, one connection per worker, commit per table).

**Environment**

- Machine / CPU: Apple M5 (Mac17,3), 10 cores
- JDK: Temurin 25.0.3+9 LTS
- Docker / DB image: OrbStack (Docker 29.4.0); `postgres:18-alpine`, `mysql:9` (Testcontainers default), `cockroachdb`
- Harness: `bench.rows=100000` (wide), `bench.warmup=1`, `bench.iterations=3`, `bench.batch=1000`; TPC-C at default cardinalities (~62k rows). Reported figure is **best of 3**.

### CPU (JMH, ops/us — higher is better)

The micro-opt replaces the per-cell `HashMap.get(column)` (hashing the value-based `Column` record)
with a positional array read. `dispatchRowIndexed` is the optimized variant of `dispatchRow` over the
same 16-column row, so the delta isolates the lookup removed (the rest is the unavoidable
`generate()` work, which dominates a row containing a 256-char varchar and a jsonb payload).

| Benchmark | Score | Notes |
|-----------|------:|-------|
| `RowDispatchBenchmark.dispatchRow` (HashMap) | 0.281 | 16-column row, per-cell map lookup |
| `RowDispatchBenchmark.dispatchRowIndexed` (array) | **0.328** | same row, positional dispatch — **+16.7%** |

(`GeneratorBenchmark` measures raw per-type generation and is unaffected by the dispatch change;
slowest types in the baseline run: `VARCHAR_LONG` 0.59, `JSONB` 2.2, `UUID` 8.7, `NUMERIC` 8.0 ops/us.)

### End-to-end (rows/sec, best of 3 — higher is better)

| Scenario | Rows | Baseline | Sequential (after) | Parallel (8 threads) | Parallel speedup |
|----------|-----:|---------:|-------------------:|---------------------:|-----------------:|
| `postgres/wide` | 1,000,000 | 126,984 | 122,923 | **414,126** | **3.26×** |
| `postgres/tpcc` | 61,983 | 75,520 | 77,535 | 83,694 | 1.11× |
| `mysql/tpcc` | 61,983 | 40,043 | 54,879 | 57,973 | 1.45× |
| `cockroach/tpcc` | 61,983 | 12,403 | 12,516 | 13,304 | 1.07× |

The headline `wide` case (1M rows across ten independent tables) at ~3× throughput — the FK-free
schema the parallel fill targets:

```mermaid
xychart-beta
    title "postgres/wide throughput — 1,000,000 rows (higher is better)"
    x-axis ["Baseline", "Sequential (micro-opt)", "Parallel (8 workers)"]
    y-axis "rows / sec" 0 --> 450000
    bar [126984, 122923, 414126]
```

Speedup of the 8-worker parallel fill over the single-connection baseline, per scenario. `wide`
parallelizes fully; TPC-C's deep, narrow foreign-key graph leaves little to run concurrently:

```mermaid
xychart-beta
    title "Parallel fill speedup vs baseline (x, higher is better)"
    x-axis ["postgres/wide", "mysql/tpcc", "postgres/tpcc", "cockroach/tpcc"]
    y-axis "speedup (x)" 0 --> 3.5
    bar [3.26, 1.45, 1.11, 1.07]
```

**Reading the numbers**

- **`wide` is the headline.** Ten independent, FK-free tables sit in a single topological level, so
  eight workers fill them concurrently: **3.26×** over the single-connection baseline. This is the
  case the parallel optimization targets.
- **TPC-C gains are modest by design.** Its foreign keys form a deep, narrow dependency graph — most
  levels hold only one or two independent tables — so there is little to parallelize within a level;
  the barrier between levels caps the win. The improvement that remains comes mostly from committing
  once per table instead of per batch.
- **CockroachDB is bound by Raft/commit latency**, not client CPU, so neither change moves it much.
- **The sequential micro-opt is within end-to-end noise.** A real fill is dominated by value
  generation and JDBC round-trips, not the per-cell lookup, so the `dispatchRowIndexed` win shows up
  clearly in JMH but not in `wide`'s sequential column (the `mysql/tpcc` sequential jump is mostly
  run-to-run variance — its baseline third iteration was an outlier). The optimization is still worth
  keeping: it is free and removes allocation/lookup from the hottest loop.

**Note on reproducibility:** the same config and seed produce identical data on every run, including
date/time/timestamp columns. `PostgresParallelFillTest` asserts a parallel fill reproduces the
sequential fill byte-for-byte across a TPC-C schema, comparing every column except the few that use
wall-clock time *by design* (e.g. the order-line delivery date), which are intentionally
non-deterministic.

## Recorded results — issue #471 (RNG migration)

[Issue #471](https://github.com/timveil/bloviate/issues/471) replaced the legacy `java.util.Random`
(a 48-bit LCG with a documented statistical defect and `synchronized` methods) with a
`java.util.random.RandomGenerator` using the JDK general-purpose default **`L64X128MixRandom`**
(`RandomGenerators.create(seed)`). The per-column seeding architecture is unchanged, so output stays
reproducible; only the algorithm changes. These numbers compare the two RNGs on the identical
benchmark harness — old jar built from `HEAD`, new jar from the migration branch.

**Environment**

- Machine / CPU: Apple M5 (Mac17,3), 10 cores
- JDK: Temurin 25.0.3+9 LTS
- Harness: `GeneratorBenchmark` / `RowDispatchBenchmark`, JMH `-f 1 -wi 3 -w 1 -i 5 -r 1` (quick settings; error bars on the clean `generate()` rows are <1%). Higher is better.

### CPU (JMH, ops/us — higher is better)

Raw value generation, old vs new RNG:

| `generate()` | `java.util.Random` | `L64X128MixRandom` | Δ |
|--------------|------------------:|-------------------:|---:|
| INTEGER | 180.8 | 318.7 | **+76%** |
| BIGINT | 91.4 | 320.5 | **+3.5×** |
| DOUBLE | 91.6 | 344.9 | **+3.8×** |
| VARCHAR_SHORT (16) | 9.5 | 17.2 | **+80%** |
| VARCHAR_LONG (256) | 0.59 | 1.32 | **+2.2×** |
| UUID | 9.4 | 9.8 | +4% |

Integer/long/double draws benefit most — no lock and a better algorithm. UUID is dominated by the
16-byte `nextBytes` copy, so it barely moves. Strings improved too: `SeededRandomUtils` no longer
delegates to commons-lang3 `RandomStringUtils` (which only accepts a `java.util.Random`); the
alphabetic/numeric paths now draw directly from a fixed character pool, which removes the
draw-and-reject loop entirely.

End-to-end per-row dispatch — `RowDispatchBenchmark` over a 16-column row, the real inner loop of
`TableFiller.fill()`:

| Benchmark | `java.util.Random` | `L64X128MixRandom` | Δ |
|-----------|------------------:|-------------------:|---:|
| `dispatchRow` (HashMap) | 0.313 | 0.607 | **+94%** |
| `dispatchRowIndexed` (array) | 0.327 | 0.661 | **+102%** |

```mermaid
xychart-beta
    title "Per-row generate() dispatch — 16-column row (ops/us, higher is better)"
    x-axis ["Random (HashMap)", "L64X128 (HashMap)", "Random (array)", "L64X128 (array)"]
    y-axis "ops / us" 0 --> 0.7
    bar [0.313, 0.607, 0.327, 0.661]
```

**Bottom line:** ~2× faster per-row generation with better statistical quality, no new dependency,
and unchanged reproducibility.
