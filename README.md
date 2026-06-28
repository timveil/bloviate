# Bloviate

[![Java CI with Maven](https://github.com/timveil/bloviate/actions/workflows/maven.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/maven.yml)
[![CodeQL](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.java.net/)

Hands-free test data generator for JDBC databases — Bloviate auto-discovers your schema, respects foreign keys, and fills tables (or CSV/TSV files) with realistic, reproducible data.

It generates *from your schema*, not from a copy of production — so the privacy story is simple and
the data is reproducible by construction. Bloviate never touches production data, is deterministic
by seed, and runs inside your JUnit/Testcontainers pipeline.

## 📖 Documentation

**Full documentation, guides, and examples live at [bloviate.io](https://bloviate.io).**

- [Quick Start](https://bloviate.io/guides/quickstart/) — install and fill a database or flat file
- [Database Support](https://bloviate.io/guides/database-support/) — PostgreSQL, MySQL, CockroachDB
- [Configuration](https://bloviate.io/guides/configuration/) — per-table/column control, distributions, seeds, parallelism
- [Generators](https://bloviate.io/guides/generators/) — registry, realistic data, composite keys, TPC-C
- [Testing Integrations](https://bloviate.io/guides/integrations/) — JUnit 5 and Testcontainers
- [File Generation](https://bloviate.io/guides/file-generation/) — CSV, TSV, pipe-delimited
- [Why Bloviate](https://bloviate.io/guides/comparison/) — how it compares

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

## 📦 Installation

Requires **Java 25+** and a JDBC-compatible database.

```xml
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-core</artifactId>
    <version>LATEST</version>
</dependency>
```

Bloviate is a multi-module build. `bloviate-core` is the dependency-free engine; the optional
integration modules (`bloviate-junit`, `bloviate-testcontainers`, `bloviate-datafaker`) pull in
their framework as a `provided` dependency. See the
[Quick Start](https://bloviate.io/guides/quickstart/) for module coordinates and the GitHub
Packages repository setup.

## 🚀 Quick Start

```java
import io.bloviate.db.DatabaseFiller;
import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.ext.PostgresSupport;

new DatabaseFiller.Builder(connection,
    new DatabaseConfiguration(
        5,    // batch size
        100,  // records per table
        new PostgresSupport(),
        null  // optional per-table configuration
    ))
    .build()
    .fill();
```

More examples — per-column overrides, distributions, composite keys, parallel fills, flat files —
are in the [documentation](https://bloviate.io).

## 🏗️ Architecture

Curious how Bloviate works under the hood — the foreign-key dependency DAG and topological fill
ordering, the schema-identity seeding that makes data reproducible, the Strategy/Registry/SPI
extension points, and the design patterns throughout? See **[ARCHITECTURE.md](ARCHITECTURE.md)** for
a technical deep-dive with diagrams.

## 🤝 Contributing

Contributions are welcome! See **[CONTRIBUTING.md](CONTRIBUTING.md)** for how to build the project,
run the test suite, spin up local databases, and submit pull requests.

- The project targets **Java 25** and uses **TestContainers** (so Docker is required to run the
  integration tests).
- Commit messages **and PR titles** follow [Conventional Commits](https://conventionalcommits.org/),
  which drives automatic semantic versioning.

The documentation site lives in [`website/`](website/) (Astro + Starlight), with the canonical
guide content under [`docs/`](docs/).

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
