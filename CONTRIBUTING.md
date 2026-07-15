# Contributing to Bloviate

Thanks for your interest in improving Bloviate! This guide covers how to build the
project locally, run the test suite, and submit changes.

## Prerequisites

- **Java 25 or higher** (the build targets `--release 25`)
- **Maven** — use the bundled wrapper (`./mvnw`); no separate install required
- **Docker** — required to run the integration tests, which spin up real databases
  via [TestContainers](https://testcontainers.com/). [OrbStack](https://orbstack.dev/)
  or Docker Desktop both work.

## Project Layout

Bloviate is a multi-module Maven build. The root `pom.xml` is the parent (packaging `pom`); the
code lives in modules:

| Module | Description |
| --- | --- |
| `bloviate-core` | The self-contained data-generation engine and flat-file support |
| `bloviate-junit` | JUnit Jupiter integration (`@FillDatabase`); JUnit is a `provided` dependency |
| `bloviate-testcontainers` | Testcontainers integration; Testcontainers is a `provided` dependency |

Shared dependency and plugin versions are managed centrally in the parent `pom.xml`. `./mvnw`
commands run from the repository root build the whole reactor.

## Building the Project

```bash
# Compile the project
./mvnw compile

# Compile, run all tests, and verify
./mvnw verify

# Package the JAR
./mvnw package

# Clean and recompile from scratch
./mvnw clean compile
```

## Running Tests

The project uses TestContainers for integration testing against real databases, so
Docker must be running.

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=PostgresFillerTest

# Run database-specific integration tests
./mvnw test -Dtest=PostgresFillerTest
./mvnw test -Dtest=MySqlFillerTest
./mvnw test -Dtest=CockroachDBFillerTest
```

Test schemas live under `bloviate-core/src/test/resources/` (TPCC, AuctionMark, Wikipedia, and
others). `BaseDatabaseTestCase` provides the shared `DataSource` plumbing and the
fidelity assertions used by the TPC-C tests.

## Databases for Testing

Integration tests rely on [Testcontainers](https://testcontainers.com/), which starts and tears
down database containers automatically — a running Docker daemon is the only prerequisite. There is
nothing to start or stop by hand.

## Design Invariants

A few properties are hard guarantees — treat a change that violates them as a breaking change,
no matter how it is labeled:

- **Seed reproducibility.** The same schema filled with the same seed must produce byte-for-byte
  identical data, including across releases. Any change to a generator's draw sequence or value
  derivation (even a faster algorithm producing "equivalent" values) breaks this and is not an
  acceptable performance optimization.
- **Deterministic ordering.** New code must not introduce iteration order that varies run to run;
  prefer insertion-ordered or explicitly sorted collections. The exception is order that is already
  load-bearing for seed compatibility: foreign-key grouping intentionally keeps its historical
  hash-based order (see the comment in `DatabaseUtils.getForeignKeys`) because changing it would
  change which parent seeds a column that participates in multiple foreign keys.

## Submitting Changes

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/amazing-feature`)
3. Make your changes, following the commit message format below
4. Ensure `./mvnw verify` passes (tests included)
5. Push to your fork (`git push origin feat/amazing-feature`)
6. Open a Pull Request

> **Note:** PR **titles** are validated by commitlint and must follow the
> Conventional Commits format described below.

### Commit & PR Title Format

This project uses [Conventional Commits](https://conventionalcommits.org/) to drive
automatic semantic versioning via semantic-release:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Commit types and version impact:**

| Type | Version bump |
|------|--------------|
| `feat:` | Minor (new feature) |
| `fix:` | Patch (bug fix) |
| `perf:` | Patch (performance) |
| `refactor:` | Patch (refactor) |
| `feat!:` or `BREAKING CHANGE:` | Major |
| `docs:`, `style:`, `test:`, `ci:`, `chore:` | None |

**Examples:**

```bash
git commit -m "feat(database): add connection pooling support"
git commit -m "fix(generator): resolve null pointer in StringGenerator"
git commit -m "docs: update installation instructions"
```

### Development Guidelines

- Follow existing code style and conventions
- Add tests for new features
- Update documentation as needed
- Ensure all tests pass (`./mvnw verify`) before submitting a PR
- Use conventional commit messages — and a conventional PR title — for automatic
  versioning

## Getting Help

- **Issues**: [GitHub Issues](https://github.com/timveil/bloviate/issues)
- **Discussions**: [GitHub Discussions](https://github.com/timveil/bloviate/discussions)
