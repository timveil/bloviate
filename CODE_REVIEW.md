# Bloviate — Code Review

**Date:** 2026-06-23
**Scope:** Full `src/main` (`io.bloviate.{db,ext,gen,gen.tpcc,file,util}`) and `src/test`, plus build/CI config.
**Focus:** Modern Java best practices (target: Java 25), DRY, correctness, and test quality.

---

## Overall assessment

Solid, readable library with a clean core design: metadata → dependency graph → topological fill, with the Builder pattern and immutable `record` models used consistently. Javadoc coverage is unusually thorough. Recent work (TPCC generators, column-config, null-getter fixes) is well-tested relative to the rest. CI is mature (CodeQL, commit-lint, dependabot, semantic-release).

The themes worth attention: a handful of **real correctness bugs**, significant **DRY/abstraction debt in the generator and support layers**, an **integration-test strategy that doesn't unit-test the hardest logic**, and some **stale/dead code and docs**.

> **Verification note:** `DataGenerator.get(ResultSet, int)` has **no caller in `src/main`** — it is exercised only by tests. The live fill path uses `generate()` / `generateAndSet()` / `setSeed()`; the flat-file path uses `generateAsString()`. So the whole "null getters" family of issues (C4) is a public-API-contract/consistency concern, not a live production bug.

---

## 1. Correctness bugs (highest value)

### C1 — `Table.filteredColumns()` can NPE on unboxing
`Table.java:81` does `!column.autoIncrement()`, but `DatabaseUtils.mapColumn` (`DatabaseUtils.java:273-279`) leaves `autoIncrement` **null** when `IS_AUTOINCREMENT` is empty/unknown (JDBC allows `""`). A null there throws `NullPointerException` during insert-string construction.
**Fix:** `Boolean.TRUE.equals(column.autoIncrement())`.

### C2 — `BigDecimalGenerator` precision/scale can throw or exceed column precision
`BigDecimalGenerator.java:40-75`. Precision is clamped to 25 (`minMaxPrecision`) but `maxDigits` (scale) is used un-clamped:
- For NUMERIC(30,28): `adjustedPrecision = 25 - 28 = -3`, and `randomNumeric(1, -2)` trips `SeededRandomUtils`' validation → `IllegalArgumentException` at generate time.
- For NUMERIC(30,30): the equal-branch (`:49-53`) emits 30 fractional digits, ignoring the clamp.
- The field-doc comments (`:31-35`) describe the two fields in swapped terms.

**Fix:** apply the clamp consistently to both precision and scale; clamp scale to `minMaxPrecision`.

### C3 — `SeededRandomUtils.nextLong` loses precision
`SeededRandomUtils.java:91` routes longs through `(long) nextDouble(...)`. With wide ranges (LongGenerator defaults to `Long.MAX_VALUE`, and all date/time generators funnel through this) the top of the range is unreachable and values are quantized to ~52 bits.
**Fix:** use `RandomGenerator.nextLong(origin, bound)` directly.

### C4 — Nullable primitive columns read back as `0`/`false` (API inconsistency)
The `get(ResultSet,int)` overrides in `IntegerGenerator:45`, `LongGenerator:45`, `ShortGenerator:41`, `DoubleGenerator:45`, `FloatGenerator:45`, `BooleanGenerator:45`, `BitGenerator:34`, and the `Static*`/`Sequential*`/`CompositeKeyComponentGenerator` call the primitive JDBC accessor with no `wasNull()` guard, so SQL NULL becomes boxed `0`. The recent branch fixed the *object* getters (`BigDecimal`, dates) but not these.
**Caveat:** `get()` is never called in `src/main` (see verification note) — this is a contract/consistency issue for library consumers, not a live fill bug. Lower urgency than its branch name implies.

### C5 — `SqlBlobGenerator`/`SqlClobGenerator.generate()` return `null`
`SqlBlobGenerator.java:23`, `SqlClobGenerator.java:23` are `//todo` stubs. A non-nullable BLOB/CLOB column fails at insert with no useful diagnostic.
**Fix:** implement, or at minimum document the null contract as was done for `SqlStructGenerator`.

### C6 — Range off-by-ones in composite generators
`IntervalGenerator.java:63-68` (month `end(12)` → never 12), `InetGenerator.java:57` (octets `1..255`, never 0). Cosmetic for dummy data but inconsistent with documented intent.

### C7 — Silent `IOException` swallowing in `FlatFileGenerator`
`FlatFileGenerator.java:115-117`, `:125-127`: write failures are logged and the method returns normally, so callers believe the file was written.
**Fix:** rethrow as unchecked.

---

## 2. Design / DRY / abstraction

### D1 — `DatabaseSupport` is a 40-method fat interface that no one implements directly
All impls extend `AbstractDatabaseSupport`, ~12 `build*` methods are `throw UnsupportedOperationException` stubs, and `getDataGenerator` is `final` over a 120-line switch (`AbstractDatabaseSupport.java:27-149`).
**Suggestion:** a `Map<JDBCType, BiFunction<Column,Random,DataGenerator<?>>>` registry that subclasses mutate would delete the interface, the switch, and most stubs — and make adding a type a one-liner.

### D2 — `PostgresSupport`, `MySQLSupport`, `DefaultSupport` are empty marker subclasses
Only CockroachDB overrides anything. Javadoc claims "PostgreSQL-specific generation" that doesn't exist.
**Suggestion:** add the real behavior or collapse them and document the truth.

### D3 — No product→support selection
Callers hardcode `new PostgresSupport()`. A factory keyed on `DatabaseMetaData.getDatabaseProductName()` (or `ServiceLoader`) is the natural library affordance and removes a foot-gun (wrong support for a DB).

### D4 — CockroachDB type dispatch is fragile string-matching
`CockroachDBSupport.java:47-70` matches driver catalog names (`"_int8"`, `"varbit"`, `"jsonb"`…) with hardcoded literals and `UnsupportedOperationException` fall-through.
**Suggestion:** extract to a constant name→generator map; current form is brittle across driver versions.

### D5 — Generator boilerplate is the single biggest DRY target
~20 generators repeat the identical `Builder(Random)` / `build()` / private `X(Builder)` / `set→setX` / `get→getX` quartet, and `start/end` range fields recur across Integer/Long/Double/Float/Short and all date/time builders.
**Suggestion:** a shared `RangeBuilder` base and a functional JDBC-accessor pair would remove hundreds of lines.

### D6 — `FileDefinition` hierarchy is redundant
`CsvFile`/`TabDelimitedFile`/`PipeDelimitedFile` each only call `super(FileType.X)`; `FileType` already carries the delimiter. Delimiters are defined twice (dead `FileType.getDelimiter()` at `FileType.java:61`, and `FlatFileGenerator.getCsvFormat()`), risking drift.
**Suggestion:** collapse to just the `FileType` enum on the builder.

### D7 — Per-row allocation in hot loops
`new SeededRandomUtils(random)` is allocated on every `generate()` call across ~10 generators (e.g. `IntegerGenerator:34`). It's a stateless wrapper.
**Fix:** hoist to a field or make the helpers static.

### D8 — `SeededRandomUtils` forbids negative ranges
`startInclusive >= 0` (lines 52/63/74/85). Signed columns never get negative values, and `IntegerGenerator.start(-5)` throws at runtime. The four range methods also duplicate the same two `Validate` lines.
**Suggestion:** document or lift the constraint; extract a `validateRange` helper.

---

## 3. Modern Java (target: 25)

- `AbstractDatabaseSupport.getDataGenerator` is an arrow-`switch` *statement* with no `default`, relying on fall-through to a throw (`:28-148`). Convert to a `switch` *expression* so the compiler enforces totality.
- Prefer `Path.of(...)` over `Paths.get(...)` (`FlatFileGenerator.java:102,124`).
- `DataGenerator` has a closed implementor set and a Jackson `@JsonTypeInfo` — a `sealed` hierarchy would be a natural fit and document the closure.
- `AbstractDataGenerator.logger` (`:51`) is a non-static package-private field, mostly unused — make `private static final` per class or drop it.
- TINYINT → `[0,255]` (`AbstractDatabaseSupport.java:152`) assumes unsigned; standard JDBC TINYINT is signed `byte`. Risks out-of-range inserts on signed columns.

---

## 4. Tests

- **Biggest gap:** the hardest logic — graph/topological ordering (`DatabaseFiller`), FK seed-map reseeding (`TableFiller.java:144`), recursive `getAssociatedPrimaryKeyColumn` — has **no container-free unit tests**; it's exercised only as a side effect of full fills, and even then FK *value equality* (the point of reseeding) is never asserted (`PostgresFillerTest.java:110-111` relies on "the fill would have thrown"). Add fast unit tests with stub `Database`/`Table` graphs (cycles, multi-level chains, self-references).
- **A fresh container per `@Test`** (`Base*Test` call `new XContainer().start()` in `fillDatabase`; CRDB runs 8) — slow and a flakiness vector. Use `@Container` singleton / `withReuse(true)`.
- **`FlatFileTest` asserts nothing** (`:28-69`) — pure smoke test, writes into `target/` instead of `@TempDir`, has a commented-out 1M-row line. Give it real content/row/delimiter assertions.
- Heavy duplication across the three `Base*Test` and three `*FillerTest` classes — prime `@ParameterizedTest` over `(support, initScript)` candidates.
- Several generator tests use unseeded `new Random()` for what are meant to be regression assertions — fix a seed for reproducibility.
- `StubResultSet` is sound (proxy-based, `wasNull()` correctly wired). `ResultSetGetterTest.stubStruct()` duplicates the idiom and could reuse it.

---

## 5. Dead code & docs

- **`ScriptRunner` (308 lines) is entirely unused** — copy-pasted MyBatis code, uses platform-default charset (`:52-53`), and has a known delimiter-mis-parse bug. Delete it.
- `DatabaseUtils.getExportedKeys` (`:174`) — private, never called.
- `FlatFileGenerator.compress` field + builder method (`:86,138,170,184`) — never consulted; implement or remove.
- `FileType.getDelimiter()` (`:61`), `StringArrayGenerator.Builder.elementGenerator` (`:75`) — dead.
- **Version docs are stale:** `pom.xml` targets `release 25`, but `README.md` badge and `CLAUDE.md` both say "Java 16."
- `getColumn` throws raw `RuntimeException` (`DatabaseUtils.java:226`) — use a typed exception.

---

## Prioritized recommendations

1. **Fix the real bugs:** C1 (NPE), C2 (BigDecimal), C3 (nextLong) — can corrupt or abort fills. *(small, high value)*
2. **Delete dead code & fix docs:** ScriptRunner, `getExportedKeys`, `compress`, the Java-version mismatch. *(trivial, removes noise)*
3. **Unit-test the core graph/FK/recursion logic** independent of containers; assert FK value equality. *(biggest coverage win)*
4. **Refactor the support layer** to a `JDBCType→factory` registry (D1) and add product-name-based selection (D3). *(removes the most structural debt)*
5. **Collapse generator builder boilerplate** (D5) and the `FileDefinition` hierarchy (D6) once tests protect you.
