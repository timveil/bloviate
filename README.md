# Bloviate

[![Java CI with Maven](https://github.com/timveil/bloviate/actions/workflows/maven.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/maven.yml)
[![CodeQL](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.java.net/)

Hands-free test data generator for JDBC databases — Bloviate auto-discovers your schema, respects foreign keys, and fills tables (or CSV/TSV files) with realistic, reproducible data.

## 🎯 Why Bloviate?

> **Bloviate never touches production data, is deterministic by seed, and runs inside your JUnit/Testcontainers pipeline.**

It generates *from your schema*, not from a copy of production — so the privacy story is simple and
the data is reproducible by construction. Five structural strengths:

1. **Zero-config foreign-key correctness.** It auto-introspects `DatabaseMetaData`, builds the FK
   dependency graph, and topologically orders the fill — parents before children, no configuration.
   Rivals make you hand-author relationships (Benerator `<reference>` selectors, Snowfakery YAML
   recipes) or require parent rows to already exist (DBeaver).
2. **Privacy-safe by construction.** Generated purely from schema and type — production data is never
   read — so the GDPR/CCPA exposure surface is structurally minimal. No masking or
   differential-privacy machinery to configure or trust.
3. **Deterministic reproducibility as a contract.** The same seed and schema produce byte-identical
   data on every run, including under parallel and partitioned fills. Most ML synthesizers can't offer
   that.
4. **CI-native, dependency-free core.** No cloud account, no Python/GPU runtime, no per-row/per-GB
   pricing — just a library on your classpath, with first-class JUnit 5
   (`@FillDatabase`/`@FillSource`) and Testcontainers integrations.
5. **Maintained and multi-DB.** PostgreSQL, MySQL, and CockroachDB, actively released — while several
   peers are dormant (synth) or feature-frozen (Benerator CE).

**Where it fits.** Bloviate is an open-source, JDBC-native *filler* — it manufactures relational data
from a schema. That's a different job from the **ML/privacy synthesizers** (SDV, Gretel, MOSTLY AI),
which learn from real data and are non-deterministic, and from the **masking/subsetting** tools
(Tonic, Delphix, Jailer), which operate on existing production data. Among its actual peers — Benerator
(JVM, but FK is manually declared and the community edition is frozen), Snowfakery (Python,
recipe-authored, no schema introspection), synth (dormant since 2022), and DBeaver Mock Data (paid,
GUI-bound) — Bloviate is the maintained, deterministic, auto-FK option.

**Honest limitations.** Bloviate is *not* a statistical/ML synthesizer (it generates *specified*
distributions, not ones learned from real data), *not* a masking/subsetting tool for existing data,
JVM-only, and has no GUI. Its core is type-driven by default — an `email VARCHAR` gets random text —
though the optional [`bloviate-datafaker`](#semantic--realistic-data-bloviate-datafaker) module adds
realistic, name-correlated values when you want them.

See **[POSITIONING.md](POSITIONING.md)** for the full competitive landscape, with sources.

## 🚀 Features

- **Automatic Schema Discovery**: Connects to existing databases and analyzes table structure, relationships, and constraints
- **Smart Data Generation**: Generates appropriate data based on column types and database-specific features
- **Foreign Key Support**: Handles complex table dependencies using topological sorting
- **Per-Column Control**: Override generation for individual columns with custom, reproducible generators
- **Composite Keys & Referential Fidelity**: Generate collision-free composite keys and variable parent/child cardinalities that keep foreign keys consistent
- **Parallel & Partitioned Fills**: Fill independent tables concurrently, and split a single dominant table's rows across workers — reproducibly, with foreign-key validity preserved
- **Benchmark-Ready**: Built-in TPC-C dataset configuration, with reusable building blocks for other benchmark schemas
- **Multiple Database Support**: PostgreSQL, MySQL, CockroachDB with extensible architecture
- **Flat File Generation**: Export data to CSV, TSV, and pipe-delimited formats
- **Configurable Output**: Control batch sizes, record counts, commit cadence, and custom data generation rules
- **Graph Visualization**: Generate DOT notation graphs of table relationships

## 📋 Table of Contents

- [Why Bloviate?](#-why-bloviate)
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Database Support](#-database-support)
- [Usage Examples](#-usage-examples)
  - [Per-Table Row Counts](#per-table-row-counts)
  - [Per-Column Generation Overrides](#per-column-generation-overrides)
  - [Value Distributions](#value-distributions)
  - [Custom Generator Registry](#custom-generator-registry)
  - [Semantic / Realistic Data (`bloviate-datafaker`)](#semantic--realistic-data-bloviate-datafaker)
  - [Correlated Columns (referential realism)](#correlated-columns-referential-realism)
  - [Composite Keys & Foreign Key Fidelity](#composite-keys--foreign-key-fidelity)
  - [Variable Parent/Child Cardinality](#variable-parentchild-cardinality)
  - [TPC-C Benchmark Data](#tpc-c-benchmark-data)
  - [Reproducible Data with Seeds](#reproducible-data-with-seeds)
  - [Parallel Table Fill](#parallel-table-fill)
  - [Commit Strategy](#commit-strategy)
  - [Testing Framework Integration](#testing-framework-integration)
  - [Flat File Formats](#flat-file-formats)
  - [Data Generator Types](#data-generator-types)
- [Configuration](#-configuration)
- [Architecture](#-architecture)
- [Contributing](#-contributing)
- [License](#-license)

## 📦 Installation

### Maven

```xml
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-core</artifactId>
    <version>LATEST</version>
</dependency>
```

Bloviate is a multi-module build. `bloviate-core` is the dependency-free engine; the optional
integration modules pull in their framework as a `provided` dependency, so you bring your own
version of JUnit / Testcontainers:

| Artifact | Use it for |
| --- | --- |
| `bloviate-core` | The data-generation engine (`DatabaseFiller`, generators, flat files) |
| `bloviate-junit` | JUnit Jupiter: fill a test database declaratively with `@FillDatabase` |
| `bloviate-testcontainers` | Fill a started `JdbcDatabaseContainer` in one call |
| `bloviate-datafaker` | Optional realistic values by column name (email, name, address …) via Datafaker |

```xml
<!-- test-scoped: JUnit 5 integration -->
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-junit</artifactId>
    <version>LATEST</version>
    <scope>test</scope>
</dependency>

<!-- test-scoped: Testcontainers integration -->
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-testcontainers</artifactId>
    <version>LATEST</version>
    <scope>test</scope>
</dependency>
```

To use GitHub Packages, add this repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/timveil/bloviate</url>
    </repository>
</repositories>
```

### Requirements

- Java 25 or higher
- JDBC-compatible database (for database filling)

## 🚀 Quick Start

### Database Filling

Fill an existing database with generated test data:

```java
import io.bloviate.db.DatabaseFiller;
import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.ext.PostgresSupport;

// Create database filler with configuration
DatabaseFiller filler = new DatabaseFiller.Builder(connection,
    new DatabaseConfiguration(
        5,    // batch size
        100,  // records per table
        new PostgresSupport(), // database-specific support
        null  // custom table configurations (optional)
    ))
    .build();

// Fill all tables with test data
filler.fill();
```

### Flat File Generation

Generate CSV files with custom column definitions:

```java
import io.bloviate.file.FlatFileGenerator;
import io.bloviate.file.ColumnDefinition;
import io.bloviate.gen.*;
import io.bloviate.util.RandomGenerators;
import java.util.random.RandomGenerator;

RandomGenerator random = RandomGenerators.create(42L);
List<ColumnDefinition> columns = Arrays.asList(
    new ColumnDefinition("id", new IntegerGenerator.Builder(random).build()),
    new ColumnDefinition("name", new SimpleStringGenerator.Builder(random).build()),
    new ColumnDefinition("email", new SimpleStringGenerator.Builder(random).build()),
    new ColumnDefinition("created_at", new DateGenerator.Builder(random).build())
);

new FlatFileGenerator.Builder("output/users")
    .addAll(columns)
    .rows(1000)
    .build()
    .generate();
```

## 🗄️ Database Support

| Database | Support Class | Coverage |
|----------|---------------|----------|
| PostgreSQL | `PostgresSupport` | Standard JDBC types **plus** PostgreSQL vendor types |
| CockroachDB | `CockroachDBSupport` | Extends `PostgresSupport` (CockroachDB is wire-compatible) |
| MySQL | `MySQLSupport` | Standard JDBC types **plus** `JSON` |
| Generic JDBC | `DefaultSupport` | Standard JDBC types only |

All four resolve the cross-database defaults for the common JDBC types (integers,
decimals, strings, dates/times, booleans, binary, …). `PostgresSupport` and
`MySQLSupport` add handling for vendor-specific types on top of those defaults.

**PostgreSQL vendor types:** `uuid`, `json`, `jsonb`, `inet`, `cidr`, `macaddr`,
`macaddr8`, `interval`, `bit`/`bit varying`, `xml`, and `text`/`integer`/`bigint`
arrays.

**MySQL vendor types:** `JSON` (generated as valid JSON rather than arbitrary text).
`ENUM`, `SET`, `GEOMETRY`, and `YEAR` are **not** supported — they need value-aware or
binary generation that standard JDBC metadata doesn't expose.

> **PostgreSQL connection requirement:** the vendor types above are bound as their text
> representations, and PostgreSQL won't implicitly cast `varchar` to `uuid`/`jsonb`/`bit`/etc.
> Open the connection with `stringtype=unspecified` so the server infers each column's type:
> `jdbc:postgresql://host/db?stringtype=unspecified`.

You can let Bloviate pick the support implementation from the connection's
metadata instead of hardcoding it:

```java
DatabaseSupport support = DatabaseSupport.forConnection(connection);
```

> **Note:** CockroachDB is reached through the PostgreSQL JDBC driver and reports its
> product name as `PostgreSQL`, so auto-selection resolves it to `PostgresSupport`.
> Because `CockroachDBSupport` extends `PostgresSupport` and adds no extra behavior, the
> two are equivalent for data generation.

## 📖 Usage Examples

By default Bloviate inspects the schema and picks a generator for every column based
on its JDBC type. The examples below show how to take progressively more control —
from overriding row counts, to overriding individual columns, all the way to
generating fully referentially-consistent benchmark datasets with composite keys.

Configuration is layered:

- **`DatabaseConfiguration`** — global defaults: batch size, default row count,
  database support, and an optional set of per-table overrides.
- **`TableConfiguration`** — overrides the row count for one table, and optionally
  carries per-column overrides.
- **`ColumnConfiguration`** — overrides how a single column is generated, via a
  `ColumnGeneratorFactory` (a `Random -> DataGenerator<?>` lambda). The engine hands
  the factory a column-seeded `Random` so output stays reproducible.

### Per-Table Row Counts

Generate different numbers of rows for specific tables while every other table uses
the default:

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

### Per-Column Generation Overrides

Pin a specific column to a custom generator. Here the `status_code` column on
`orders` is constrained to integers in `[1, 10)` instead of the type's default:

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

Column names are matched **case-insensitively**. Any column without an override keeps
its default, type-based generator.

### Value Distributions

Real columns are rarely uniform — a `status` is mostly `ACTIVE`, a `rating` clusters around its
mean, a referenced `product_id` follows a popularity curve, and a `created_at` bunches toward the
present. The `Distributions` helper returns ready-made `ColumnGeneratorFactory` values so a column can
opt into a **non-uniform distribution** without writing a factory:

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

Available shapes: `weighted(...)` (categorical), `normal(...)` / `normalInt(...)` (bounded Gaussian),
`zipfian(...)` (power-law), and `recentTimestamps(...)` (recency-skewed). Each is built from the
engine's column seed, so output stays **reproducible** and composes with foreign-key reseeding and
parallel fills like any other generator. These are *specified* distributions, not distributions
learned from real data.

### Custom Generator Registry

Per-column overrides are precise but must be wired one column at a time. When you want a
custom generator applied **broadly** — every column named `email`, every `uuid` vendor
type, or every `INTEGER` — register it once on a `GeneratorRegistry` and attach it to the
`DatabaseConfiguration`. No subclassing of `DatabaseSupport` required.

```java
import io.bloviate.db.*;
import io.bloviate.ext.GeneratorRegistry;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.*;
import java.sql.JDBCType;
import java.util.Set;

GeneratorRegistry registry = new GeneratorRegistry.Builder()
    // by column-name pattern (case-insensitive regex, full match)
    .registerColumnNamePattern("email",
        (column, random) -> new SimpleStringGenerator.Builder(random).size(column.maxSize()).build())
    // by vendor type name (case-insensitive)
    .registerTypeName("uuid",
        (column, random) -> new UUIDGenerator.Builder(random).build())
    // by JDBCType (overrides the built-in default for that type)
    .registerJdbcType(JDBCType.INTEGER,
        (column, random) -> new IntegerGenerator.Builder(random).start(1).end(1000).build())
    // auto-discover GeneratorPlugins contributed by other jars on the classpath
    .discover()
    .build();

DatabaseConfiguration config = new DatabaseConfiguration(
    128, 100, new PostgresSupport(), null, registry);

new DatabaseFiller.Builder(connection, config)
    .build()
    .fill();
```

**Resolution precedence** (first match wins, highest to lowest):

1. per-column `ColumnConfiguration` (from a `TableConfiguration`)
2. registry **column-name pattern**
3. registry **vendor type name**
4. registry **JDBCType**
5. built-in `DatabaseSupport` default

Every path is constructed with the engine's seeded `Random`, so custom generators stay
**reproducible** exactly like the built-ins.

**Plugin discovery.** Third-party jars can contribute rules automatically by implementing
`io.bloviate.ext.GeneratorPlugin` and declaring it in
`META-INF/services/io.bloviate.ext.GeneratorPlugin`. Calling `.discover()` on the builder
loads every such plugin via `ServiceLoader`:

```java
public class MyGenerators implements GeneratorPlugin {
    @Override
    public void contribute(GeneratorRegistry.Builder builder) {
        builder.registerTypeName("geometry",
            (column, random) -> new MyGeometryGenerator.Builder(random).build());
    }
}
```

### Semantic / Realistic Data (`bloviate-datafaker`)

By default an `email VARCHAR` gets random text, not a plausible email. The optional
**`bloviate-datafaker`** module maps common column **names** (`email`, `first_name`, `phone`, `city`,
`zip`, …) to realistic [Datafaker](https://www.datafaker.net/) values — keeping the core
dependency-free (Datafaker only lands if you add this module).

It's **opt-in twice over**: add the module, and build a `GeneratorRegistry` that includes its plugin —
either by `discover()` (it registers via `ServiceLoader`) or explicitly:

```java
import io.bloviate.datafaker.DatafakerGeneratorPlugin;
import io.bloviate.ext.GeneratorRegistry;

GeneratorRegistry registry = new GeneratorRegistry.Builder()
    .discover()                                  // picks up DatafakerGeneratorPlugin from the classpath
    .build();

DatabaseConfiguration config = new DatabaseConfiguration(
    128, 100, new PostgresSupport(), null, registry);   // registry sits between per-column overrides and type defaults
```

So an `email` column now yields `jane.doe@example.com`, `first_name` a real first name, `phone` a
`(555) 555-01xx` number, and so on. Properties:

- **Reproducible** — realistic values are seeded from the engine's column seed, so the same seed gives
  the same data; the locale is fixed (default `Locale.ENGLISH`) so output is identical across machines.
- **Safe by default** — reserved `example.*` email domains and `555-01xx` phone numbers (never real
  identifiers); values are truncated to the column's length.
- **Unmatched columns are untouched** — anything the dictionary doesn't recognize falls through to the
  normal type-based generator. Don't route `UNIQUE`/primary-key columns through it — realistic
  dictionaries repeat at scale, so keep Bloviate's sequence/seeded generators for keys.

### Correlated Columns (referential realism)

The plugin above makes each column realistic *independently* — so a row's `email` won't match its
`first_name`/`last_name`. For columns that should agree, share one **`RowContext`** and register each as
a **projection** of the same per-row entity. `People.context(...)` provides a coherent person whose
`email`, `username`, and `full_name` are derived from the row's name:

```java
import io.bloviate.datafaker.People;
import io.bloviate.datafaker.Person;
import io.bloviate.datafaker.RowContext;

RowContext<Person> person = People.context(42L, java.util.Locale.ENGLISH);

Set<ColumnConfiguration> columns = Set.of(
    new ColumnConfiguration("first_name", person.project(Person::firstName)),
    new ColumnConfiguration("last_name",  person.project(Person::lastName)),
    new ColumnConfiguration("full_name",  person.project(Person::fullName)),
    new ColumnConfiguration("email",      person.project(Person::email)),     // jane.doe7@example.com
    new ColumnConfiguration("username",   person.project(Person::username))   // jane.doe7
);
```

Now `full_name` matches `first_name`/`last_name`, and `email`/`username` are derived from them. The
entity is a pure function of `(seed, rowIndex)`, so the data is **reproducible and order-independent**
— sequential and parallel/partitioned fills produce identical values. Email and username carry a
row-index suffix so they stay unique at scale.

> The first release covers the **person** entity. Consistent geo tuples (a `city`/`state`/`zip` that
> agree) need a bundled reference dataset and are tracked as the next step on
> [#473](https://github.com/timveil/bloviate/issues/473).

### Composite Keys & Foreign Key Fidelity

For schemas with composite primary keys, `CompositeKeyComponentGenerator` fills each
key component as one dimension of a dense, row-ordered **cartesian product**. Each
component emits `start + ((rowNumber / repeat) % cycle)`, so a table receives exactly
its cartesian cardinality of rows with unique, collision-free keys — and a child
table reusing the *same* formula produces foreign keys that line up exactly with its
parent's keys.

For example, a `stock` table keyed by `(s_w_id, s_i_id)` across 2 warehouses × 10
items (20 rows) — `repeat` for the outer dimension is the size of the inner one:

```java
import io.bloviate.db.*;
import io.bloviate.gen.CompositeKeyComponentGenerator;
import java.util.Set;

int warehouses = 2, items = 10;

Set<ColumnConfiguration> stockKey = Set.of(
    // outer dimension: changes every `items` rows, cycles through warehouses
    new ColumnConfiguration("s_w_id",
        r -> new CompositeKeyComponentGenerator.Builder(r)
                .start(1).repeat(items).cycle(warehouses).build()),
    // inner dimension: changes every row, cycles through items
    new ColumnConfiguration("s_i_id",
        r -> new CompositeKeyComponentGenerator.Builder(r)
                .start(1).repeat(1).cycle(items).build())
);

Set<TableConfiguration> tableConfigs = Set.of(
    new TableConfiguration("stock", (long) warehouses * items, stockKey)
);
// → (1,1) (1,2) ... (1,10) (2,1) ... (2,10): every pair exactly once
```

### Variable Parent/Child Cardinality

Real schemas rarely have a fixed number of children per parent (an order has *some*
number of line items). `ChildCardinality` assigns each parent a deterministic child
count in `[min, max]`, and the matching generators keep a parent table and its child
table perfectly in sync — no shared mutable state, O(1) memory, reproducible from a
seed.

```java
import io.bloviate.db.*;
import io.bloviate.gen.*;
import java.util.Set;

long orders = 1000;

// each order gets 1–10 lines; the same seed makes the split reproducible
ChildCardinality cardinality = new ChildCardinality(1, 10, 42L);
long orderLineRows = cardinality.total(orders); // exact total, computed up front

// parent: a column holding each order's line count
Set<ColumnConfiguration> orderCols = Set.of(
    new ColumnConfiguration("line_count",
        r -> new ChildCountGenerator.Builder(r).cardinality(cardinality).build())
);

// child: parent key repeated per line, plus a 1-based line sequence
Set<ColumnConfiguration> lineCols = Set.of(
    new ColumnConfiguration("order_id",
        r -> new ChildKeyComponentGenerator.Builder(r)
                .cardinality(cardinality).start(1).repeat(1).cycle((int) orders).build()),
    new ColumnConfiguration("line_number",
        r -> new ChildKeyComponentGenerator.Builder(r)
                .cardinality(cardinality).sequence().start(1).build())
);

Set<TableConfiguration> tableConfigs = Set.of(
    new TableConfiguration("orders", orders, orderCols),
    new TableConfiguration("order_lines", orderLineRows, lineCols)
);
```

### TPC-C Benchmark Data

Bloviate ships a ready-made configuration for the full **TPC-C** schema. A single
call wires up all nine tables with spec-faithful cardinalities, composite keys,
foreign-key fidelity, variable order-line counts, per-district customer-id
permutations, deterministic last-name enumeration, and delivery state — matching the
TPC-C initial database population (clause 4.3.3.1).

```java
import io.bloviate.db.*;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import java.util.Set;

// Build all TPC-C table configs for a given scale factor (number of warehouses)
Set<TableConfiguration> tpcc = TPCCConfiguration.build(2);

DatabaseConfiguration config = new DatabaseConfiguration(
    128, 100, new PostgresSupport(), tpcc);

new DatabaseFiller.Builder(connection, config)
    .build()
    .fill();
```

Need finer control over the cardinalities? An overload exposes every dimension:

```java
Set<TableConfiguration> tpcc = TPCCConfiguration.build(
    2,    // warehouses (scale factor)
    100,  // items
    10,   // districts per warehouse
    3000, // customers (and orders) per district
    5,    // min order lines per order
    15    // max order lines per order
);
```

The corresponding DDL lives in
`bloviate-core/src/test/resources/create_tpcc.{postgres,mysql,cockroachdb}.sql`.
The reusable building blocks behind `TPCCConfiguration` — `CompositeKeyComponentGenerator`,
`ChildCardinality`, `GroupedPermutationGenerator` (per-group shuffles), and
`GroupedPrefixGenerator` (nullable/sparse columns) — live in `io.bloviate.gen` and can
be composed for other benchmark schemas (TPC-H, TPC-DS, and so on).

### Reproducible Data with Seeds

`DatabaseConfiguration` takes a base **seed**. The same schema filled with the same seed always
produces identical data, so test fixtures are deterministic; change the seed for a different —
but still reproducible — dataset. Per-column seeds are derived from stable column identity, and
foreign keys are seeded from their referenced primary key, so referential fidelity holds for any
seed.

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

### Parallel Table Fill

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
tables within each level concurrently, **one connection per worker**, barriering between levels so a
child table is never filled before its parent. Each worker fills its table in a single transaction
(commit once per table). The fill stays **fully reproducible**: a table's data depends only on its
own seed and row order, never on which tables fill alongside it, so the same config and seed produce
byte-for-byte the same data as a sequential fill.

How much this helps depends on the schema. A wide schema of independent tables sees a large speedup
(~3× with 8 workers on a 10-table, 1M-row fixture); a deep, narrow foreign-key chain (each table
depending on the previous) has little to parallelize. See [BENCHMARKS.md](BENCHMARKS.md) for numbers.

The single-`Connection` constructor is unchanged and remains the default sequential path — `threads`
only applies to the `DataSource` form.

#### Intra-table partitioning

When a **single large table dominates** the fill, between-table parallelism can't help it — it sits
alone in its topological level. Set `partitions` on that table's `TableConfiguration` to split its
rows into that many contiguous ranges filled concurrently, one connection per range, on the parallel
(`DataSource` + `threads`) path:

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

Size the connection pool for the total concurrent demand (`threads`, where a partitioned table counts
as `partitions` units). One case is unsupported: partitioning a **parent** table whose primary key is
a plain *random* generator referenced by a foreign key can orphan those references — partition the
child table instead, or use the positional key generators (as the bundled TPC-C/TPC-H configurations
do). A custom generator with internal positional state must implement
`io.bloviate.gen.IndexedDataGenerator` to stay aligned under partitioning.

### Commit Strategy

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
touches autocommit). The parallel path already commits once per table; a configured strategy applies
there too.

> **Tip — driver batch rewrite.** Bloviate inserts in JDBC batches, but most drivers only collapse a
> batch into a single multi-row `INSERT` when you opt in via the JDBC URL: PostgreSQL
> `reWriteBatchedInserts=true`, MySQL `rewriteBatchedStatements=true`. Enabling it is often the
> single biggest fill speedup, sequential or parallel. Bloviate **logs a warning** at fill time when
> the parameter is missing, and `io.bloviate.util.JdbcUrls.withBatchRewrite(url, support.batchRewriteUrlParameter())`
> builds a correctly-parameterized URL if you construct the `DataSource` yourself. CockroachDB ignores
> the parameter, so no warning is emitted there.

### Testing Framework Integration

#### JUnit 5 (`bloviate-junit`)

Fill a test database declaratively. Annotate the test with `@FillDatabase` and mark the
`DataSource`/`Connection` to fill with `@FillSource`; the data is regenerated before each test,
reproducibly via `seed`. Create the schema first (init script, Flyway/Liquibase, etc.) — Bloviate
fills the tables it discovers.

```java
import io.bloviate.junit.FillDatabase;
import io.bloviate.junit.FillSource;
import javax.sql.DataSource;

@FillDatabase(rows = 250, seed = 42)
class OrderRepositoryTest {

    @FillSource
    static DataSource dataSource = createDataSource(); // schema already created

    @Test
    void queriesGeneratedData() throws Exception {
        // dataSource now holds 250 reproducible rows per table
    }

    @Test
    @FillDatabase(rows = 5, seed = 7) // method-level overrides the class default
    void worksWithASmallDataset() throws Exception {
        // ...
    }
}
```

`@FillDatabase` is meta-annotated with `@ExtendWith(BloviateExtension.class)`, so no separate
`@ExtendWith` is needed. The database support is auto-detected from the connection.

#### Testcontainers (`bloviate-testcontainers`)

Fill a started `JdbcDatabaseContainer` in one call — the public form of the pattern Bloviate uses
in its own tests:

```java
import io.bloviate.testcontainers.BloviateContainers;
import org.testcontainers.containers.PostgreSQLContainer;

try (var postgres = new PostgreSQLContainer<>("postgres:18-alpine")
        .withInitScript("schema.sql")) {  // creates the schema on startup
    postgres.start();

    BloviateContainers.forContainer(postgres)
        .rows(500)
        .seed(42)
        .fill();

    // ... assert against the filled database
}
```

### Flat File Formats

```java
import io.bloviate.file.*;

// Tab-delimited file
new FlatFileGenerator.Builder("data/output")
    .output(new TabDelimitedFile())
    .addAll(columnDefinitions)
    .rows(5000)
    .build()
    .generate();

// Pipe-delimited file
new FlatFileGenerator.Builder("data/output")
    .output(new PipeDelimitedFile())
    .addAll(columnDefinitions)
    .rows(5000)
    .build()
    .generate();
```

### Data Generator Types

Bloviate includes generators for all common database types:

```java
// Numeric generators (ranges are [start, end))
new IntegerGenerator.Builder(random).start(1).end(1000).build()
new DoubleGenerator.Builder(random).start(0.0).end(100.0).build()
new BigDecimalGenerator.Builder(random).precision(10).digits(2).build()

// String generators
new SimpleStringGenerator.Builder(random).size(20).build()
new VariableStringGenerator.Builder(random).minLength(5).maxLength(50).build()
new UUIDGenerator.Builder(random).build()

// Date/Time generators
new DateGenerator.Builder(random).build()
new SqlTimestampGenerator.Builder(random).build()
new InstantGenerator.Builder(random).build()

// Boolean and specialized generators
new BooleanGenerator.Builder(random).build()
new JsonbGenerator.Builder(random).build()
new InetGenerator.Builder(random).build()

// Constant / fixed-scale generators (handy for column overrides)
new StaticStringGenerator.Builder(random).value("OE").build()
new StaticIntegerGenerator.Builder(random).value(0).build()
new ScaledBigDecimalGenerator.Builder(random).start(0.0).end(0.2).scale(4).build()

// Deterministic / sequential generators
new SequentialIntegerGenerator.Builder(random).start(1).end(1000).build()
```

Additional generators cover `Long`, `Float`, `Byte`, `Short`, `Character`,
`java.sql` date/time types, arrays, and CockroachDB-specific types (bit strings,
intervals). See the `io.bloviate.gen` package for the full set.

## ⚙️ Configuration

### Database Configuration Options

- **Batch Size**: Number of records inserted in each batch operation
- **Record Count**: Default number of records to generate per table
- **Database Support**: Database-specific implementation for optimal compatibility
- **Table Configurations**: Override the row count for specific tables, and the intra-table
  `partitions` count for splitting a large table across workers on the parallel path
- **Column Configurations**: Override the generator for specific columns (case-insensitive, reproducible)
- **Seed**: Base seed for reproducible generation; the same schema and seed always produce the
  same data (defaults to `0`)
- **Commit Strategy**: How the engine commits — leave autocommit alone (default), commit once per
  table, or commit every N batches (see [Commit Strategy](#commit-strategy))

Parallelism (worker threads for concurrent table fill) is configured on the `DatabaseFiller.Builder`
via `threads(n)` with the `DataSource` constructor — see [Parallel Table Fill](#parallel-table-fill).

### File Generation Options

- **Output Format**: CSV, TSV, or pipe-delimited
- **Row Count**: Number of rows to generate
- **Custom Column Definitions**: Full control over data generation

## 🏗️ Architecture

Curious how Bloviate works under the hood — the foreign-key dependency DAG and topological fill
ordering, the schema-identity seeding that makes data reproducible, the Strategy/Registry/SPI
extension points, and the design patterns throughout? See **[ARCHITECTURE.md](ARCHITECTURE.md)** for
a technical deep-dive with diagrams.

## 🤝 Contributing

Contributions are welcome! See **[CONTRIBUTING.md](CONTRIBUTING.md)** for how to build
the project, run the test suite, spin up local databases, and submit pull requests.

A couple of highlights:

- The project targets **Java 25** and uses **TestContainers** (so Docker is required to
  run the integration tests).
- Commit messages **and PR titles** follow [Conventional Commits](https://conventionalcommits.org/),
  which drives automatic semantic versioning.

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙋‍♂️ Support

- **Issues**: [GitHub Issues](https://github.com/timveil/bloviate/issues)
- **Discussions**: [GitHub Discussions](https://github.com/timveil/bloviate/discussions)

## 📈 Roadmap

- [ ] [Additional database support (Oracle, SQL Server)](https://github.com/timveil/bloviate/issues/445)
- [x] [Custom data generation plugins](https://github.com/timveil/bloviate/issues/446)
- [x] [Performance optimizations for large datasets](https://github.com/timveil/bloviate/issues/447) — _parallel table fill, intra-table partitioning, configurable commit strategy, batch-rewrite surfacing, and hot-loop dispatch ([benchmarks](BENCHMARKS.md))_
- [ ] GUI for configuration management
- [x] [Integration with popular testing frameworks](https://github.com/timveil/bloviate/issues/448) — _JUnit 5 (`bloviate-junit`) and Testcontainers (`bloviate-testcontainers`)_

---

<div align="center">
Made with ❤️ by <a href="https://github.com/timveil">Tim Veil</a>
</div>