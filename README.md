# Bloviate

[![Java CI with Maven](https://github.com/timveil/bloviate/actions/workflows/maven.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/maven.yml)
[![CodeQL](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.java.net/)

A powerful Java library for generating realistic test data for JDBC-compatible relational databases and flat files. Bloviate automatically analyzes your database schema and generates appropriate data while respecting foreign key relationships and constraints.

## 🚀 Features

- **Automatic Schema Discovery**: Connects to existing databases and analyzes table structure, relationships, and constraints
- **Smart Data Generation**: Generates appropriate data based on column types and database-specific features
- **Foreign Key Support**: Handles complex table dependencies using topological sorting
- **Per-Column Control**: Override generation for individual columns with custom, reproducible generators
- **Composite Keys & Referential Fidelity**: Generate collision-free composite keys and variable parent/child cardinalities that keep foreign keys consistent
- **Benchmark-Ready**: Built-in TPC-C dataset configuration, with reusable building blocks for other benchmark schemas
- **Multiple Database Support**: PostgreSQL, MySQL, CockroachDB with extensible architecture
- **Flat File Generation**: Export data to CSV, TSV, and pipe-delimited formats
- **Configurable Output**: Control batch sizes, record counts, and custom data generation rules
- **Graph Visualization**: Generate DOT notation graphs of table relationships

## 📋 Table of Contents

- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Database Support](#-database-support)
- [Usage Examples](#-usage-examples)
  - [Per-Table Row Counts](#per-table-row-counts)
  - [Per-Column Generation Overrides](#per-column-generation-overrides)
  - [Composite Keys & Foreign Key Fidelity](#composite-keys--foreign-key-fidelity)
  - [Variable Parent/Child Cardinality](#variable-parentchild-cardinality)
  - [TPC-C Benchmark Data](#tpc-c-benchmark-data)
  - [Flat File Formats](#flat-file-formats)
  - [Data Generator Types](#data-generator-types)
- [Configuration](#-configuration)
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

Random random = new Random();
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

| Database | Support Class | Status |
|----------|---------------|--------|
| PostgreSQL | `PostgresSupport` | ✅ Full Support |
| MySQL | `MySQLSupport` | ✅ Full Support |
| CockroachDB | `CockroachDBSupport` | ✅ Full Support |
| Generic JDBC | `DefaultSupport` | ⚠️ Basic Support |

You can let Bloviate pick the support implementation from the connection's
metadata instead of hardcoding it:

```java
DatabaseSupport support = DatabaseSupport.forConnection(connection);
```

> **Note:** CockroachDB is reached through the PostgreSQL JDBC driver and
> reports its product name as `PostgreSQL`, so auto-selection resolves it to
> `PostgresSupport`. To use the CockroachDB-specific generators, pass
> `new CockroachDBSupport()` explicitly.

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

// ColumnGeneratorFactory is a `Random -> DataGenerator<?>` lambda.
// The engine supplies a column-seeded Random for reproducible output.
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

The corresponding DDL lives in `src/test/resources/create_tpcc.{postgres,mysql,cockroachdb}.sql`.
The reusable building blocks behind `TPCCConfiguration` — `CompositeKeyComponentGenerator`,
`ChildCardinality`, `GroupedPermutationGenerator` (per-group shuffles), and
`GroupedPrefixGenerator` (nullable/sparse columns) — live in `io.bloviate.gen` and can
be composed for other benchmark schemas (TPC-H, TPC-DS, and so on).

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
- **Table Configurations**: Override the row count for specific tables
- **Column Configurations**: Override the generator for specific columns (case-insensitive, reproducible)

### File Generation Options

- **Output Format**: CSV, TSV, or pipe-delimited
- **Row Count**: Number of rows to generate
- **Custom Column Definitions**: Full control over data generation

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
- **Email**: tjveil@gmail.com

## 📈 Roadmap

- [ ] Additional database support (Oracle, SQL Server)
- [ ] Custom data generation plugins
- [ ] Performance optimizations for large datasets
- [ ] GUI for configuration management
- [ ] Integration with popular testing frameworks

---

<div align="center">
Made with ❤️ by <a href="https://github.com/timveil">Tim Veil</a>
</div>