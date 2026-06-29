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
- [Architecture](https://bloviate.io/guides/architecture/) — FK dependency DAG, topological fill, deterministic seeding, extension points
- [Benchmarks](https://bloviate.io/guides/benchmarks/) — JMH micro-benchmarks and end-to-end fill throughput
- [Why Bloviate](https://bloviate.io/guides/comparison/) — where it fits and how it compares

## 🚀 Features

- **Automatic schema discovery** with **foreign-key-aware** fill ordering (topological sort)
- **Deterministic by seed** — same seed + schema ⇒ byte-identical data, even under parallel fills
- **Per-column control** and **pluggable generators**; realistic semantic values via Datafaker
- **Parallel & partitioned fills** for large datasets, with referential integrity preserved
- **PostgreSQL, MySQL, CockroachDB**, plus CSV/TSV/pipe **flat-file** output
- First-class **JUnit 5** and **Testcontainers** integrations

See the [full feature tour and guides on bloviate.io](https://bloviate.io).

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

## 🏗️ Architecture & benchmarks

Curious how Bloviate works under the hood — the foreign-key dependency DAG and topological fill
ordering, the schema-identity seeding that makes data reproducible, and the Strategy/Registry/SPI
extension points? The technical deep-dive (with diagrams) and the performance numbers now live on
the site:

- **[Architecture](https://bloviate.io/guides/architecture/)** — design, diagrams, extension points
- **[Benchmarks](https://bloviate.io/guides/benchmarks/)** — JMH and end-to-end fill throughput

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
- [x] [Performance optimizations for large datasets](https://github.com/timveil/bloviate/issues/447) — _parallel table fill, intra-table partitioning, configurable commit strategy, batch-rewrite surfacing, and hot-loop dispatch ([benchmarks](https://bloviate.io/guides/benchmarks/))_
- [ ] GUI for configuration management
- [x] [Integration with popular testing frameworks](https://github.com/timveil/bloviate/issues/448) — _JUnit 5 (`bloviate-junit`) and Testcontainers (`bloviate-testcontainers`)_

---

<div align="center">
Made with ❤️ by <a href="https://github.com/timveil">Tim Veil</a>
</div>
