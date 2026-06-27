# Contributing to Bloviate

Thanks for your interest in improving Bloviate! This guide covers how to build the
project locally, run the test suite, and submit changes.

## Prerequisites

- **Java 25 or higher** (the build targets `--release 25`)
- **Maven** — use the bundled wrapper (`./mvnw`); no separate install required
- **Docker** — required to run the integration tests, which spin up real databases
  via [TestContainers](https://testcontainers.com/). [OrbStack](https://orbstack.dev/)
  or Docker Desktop both work.

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

Test schemas live under `src/test/resources/` (TPCC, AuctionMark, Wikipedia, and
others). `BaseDatabaseTestCase` provides the shared `DataSource` plumbing and the
fidelity assertions used by the TPC-C tests.

## Local Databases with Docker

The `docker/` directory contains throwaway database instances for manual
experimentation (separate from the TestContainers used in tests):

```bash
# PostgreSQL
cd docker/postgres && ./up.sh

# MySQL
cd docker/mysql && ./up.sh

# CockroachDB
cd docker/crdb && ./up.sh
```

Each directory provides `up.sh`, `down.sh`, and `prune.sh` scripts.

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
- **Email**: tjveil@gmail.com
