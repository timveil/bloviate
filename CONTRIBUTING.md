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

## Static Analysis

Two checks run at the `validate` phase — before anything is compiled — so a problem
fails in seconds rather than after the integration suite has started containers. Both
are part of `./mvnw verify`; there is no separate command to remember.

| Check | Enforces |
| --- | --- |
| `maven-enforcer-plugin` | Maven `[3.9.0,)`, Java `[25,)`, no duplicated dependency versions, and dependency convergence |
| `spotless-maven-plugin` | The Apache-2.0 license header on every `.java` file, no trailing whitespace, newline at EOF |

If Spotless reports a violation, fix it automatically:

```bash
./mvnw spotless:apply
```

The canonical header lives in `license-header.txt` at the repository root; new source
files must start with it verbatim. Spotless is deliberately configured for headers and
whitespace only — it does **not** impose a Java formatter, so existing code layout and
`git blame` history are left alone.

Dependency convergence is enforced because Bloviate is not shaded: every transitive
version is one a consumer actually inherits. When a new dependency introduces a
conflict, resolve it with an explicit `dependencyManagement` pin in the parent
`pom.xml` rather than relying on Maven's nearest-wins tiebreak.

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

A few properties are hard guarantees:

- **Seed reproducibility within a version.** For a given Bloviate version, the same schema filled
  with the same seed must produce byte-for-byte identical data on every run, on every platform and
  JDK. Nothing that feeds generation may depend on run-to-run or JDK-dependent state (hash iteration
  order, wall-clock time, identity hash codes, default locale/timezone).
- **Cross-version changes are allowed but must be deliberate.** A new release may change the data a
  seed produces (e.g. a fixed traversal order or an improved generator), but the change must be
  intentional, called out in the release notes, and accompanied by regenerating the golden dump in
  `SeedGoldenDumpTest` — never an accidental side effect of a refactor. The golden-dump test exists
  to turn any unintentional drift into a loud failure.

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
