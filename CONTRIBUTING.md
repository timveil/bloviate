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

All of the following are part of `./mvnw verify`; there is no separate command to
remember. The first two run at `validate`, before anything is compiled, so a problem
fails in seconds rather than after the integration suite has started containers.

| Check | Phase | Enforces | Fails the build |
| --- | --- | --- | --- |
| `maven-enforcer-plugin` | `validate` | Maven `[3.9.0,)`, Java `[25,)`, no duplicated dependency versions, dependency convergence | yes |
| `spotless-maven-plugin` | `validate` | Apache-2.0 license header on every `.java` file, no trailing whitespace, newline at EOF | yes |
| `spotbugs-maven-plugin` | `verify` | Bytecode analysis, `effort=Max`, `threshold=Medium` | yes |
| `maven-pmd-plugin` (PMD) | `verify` | Source analysis against a curated ruleset | yes |
| `maven-pmd-plugin` (CPD) | `verify` | Copy-paste blocks of 100+ tokens | no — advisory |
| `jacoco-maven-plugin` | `verify` | Per-package line/branch coverage floors | yes |

Shared configuration lives at the repository root so all five modules use one copy:

```
config/pmd/ruleset.xml        PMD rules and the exclusions, each with its rationale
config/spotbugs/exclude.xml   SpotBugs suppressions, each with its rationale
license-header.txt            the canonical Apache-2.0 header
```

CPD is still advisory; its count appears in the build output and the CI job summary, and
it will be flipped to failing once the remaining duplication is collapsed.

When adding a suppression, scope it as narrowly as the finding allows and say why it is
safe. A pattern suppressed repository-wide hides the next genuine instance of it. Prefer,
in order:

1. **Fix the code**, when the tool is right.
2. **`@SuppressWarnings("PMD.RuleName")` on the smallest enclosing element**, with a comment
   giving the reason — this keeps the rule live everywhere else.
3. **A ruleset exclusion**, only when the pattern is deliberate across the whole codebase
   (a builder convention, an SQL NULL fidelity requirement) rather than local to one method.

Two traps worth knowing when editing `config/pmd/ruleset.xml`:

- **An `<exclude>` in the wrong category block silently does nothing.** Rules live in
  specific categories, and excluding `Foo` from `bestpractices` when it belongs to
  `errorprone` is a no-op — the build still passes and the report still generates. PMD logs
  `Exclude pattern 'Foo' did not match any rule in ruleset '...'`, so grep the build output
  for `did not match any rule` after every ruleset edit.
- **XML comments cannot contain `--`.** A comment mentioning `i--` or `--flag` makes the
  ruleset unparseable, and the resulting failure names a generated file under
  `target/pmd/rulesets/`, not the file you edited.

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

In CI a dedicated `static-analysis` job runs these checks without the integration
tests, so a formatting or analysis problem reports in about a minute rather than
waiting on Docker. The reports (`spotbugsXml.xml`, `pmd.xml`, `cpd.xml`) are uploaded
as build artifacts, and a summary table appears on the workflow run page.

A CycloneDX SBOM is generated at `package`. Each module gets its own `target/bom.xml`
covering just that module's dependencies, and the reactor root gets an aggregate; both
are attached to the build so releases carry a component inventory.

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
