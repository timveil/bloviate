# Bloviate — Code Review

**Original review:** 2026-06-23
**Scope:** Full `src/main` (`io.bloviate.{db,ext,gen,gen.tpcc,file,util}`) and `src/test`, plus build/CI config.
**Focus:** Modern Java best practices (target: Java 25), DRY, correctness, and test quality.

---

## Status

Most of the prioritized findings have been addressed across five PRs. Each finding below is tagged:
✅ **done** · 🔧 **partial** · ⬜ **open / deferred**.

| PR | Theme |
|----|-------|
| #424 | C1/C2/C3 correctness fixes, dead-code + stale-doc cleanup, FK-chain & fill-ordering unit tests |
| #425 | Cache `SeededRandomUtils` instead of per-call allocation (D7) |
| #426 | Replace the `DatabaseSupport` type switch with a `JDBCType` registry (D1, breaking) |
| #427 | Product-name-based `DatabaseSupport` selection (D3) |
| #428 | Null-returning primitive `get()` getters (C4) |

**Still open** (intentionally deferred or lower-priority): C5, C6, C7; D2, D4, D6, D8; the modern-Java polish items; and the remaining test-infrastructure improvements. Details inline.

---

## 1. Correctness bugs

### ✅ C1 — `Table.filteredColumns()` NPE on unboxing — *fixed in #424*
`Table.java` unboxed a nullable `autoIncrement` Boolean. Now guarded with `Boolean.TRUE.equals(...)`.

### ✅ C2 — `BigDecimalGenerator` precision/scale — *fixed in #424*
Scale was used un-capped while precision was clamped to 25, throwing on e.g. `NUMERIC(30,28)`. Scale is now capped to the reduced precision; covered by `BigDecimalGeneratorTest`.

### ✅ C3 — `SeededRandomUtils.nextLong` precision — *fixed in #424*
No longer routes longs through `double`; uses `RandomGenerator.nextLong(origin, bound)` directly.

### ✅ C4 — primitive getters returned `0`/`false` for SQL NULL — *fixed in #428*
All twelve primitive-typed `get(ResultSet,int)` methods now guard with `wasNull()`. `ResultSetGetterTest` covers value + null for each. Note: `get()` still has no caller in `src/main` — this was an API-consistency fix, not a live fill bug.

### ⬜ C5 — `SqlBlobGenerator`/`SqlClobGenerator.generate()` return `null`
Still `//todo` stubs; a non-nullable BLOB/CLOB column fails at insert with no useful diagnostic. Implement, or document the null contract as `SqlStructGenerator` does.

### ⬜ C6 — range off-by-ones in composite generators
`IntervalGenerator` (month never 12) and `InetGenerator` (octets `1..255`, never 0) remain. Cosmetic for dummy data.

### ⬜ C7 — silent `IOException` swallowing in `FlatFileGenerator`
`generate()` / `yaml()` still log and return normally on write failure; callers can't tell the file wasn't written. Rethrow as unchecked.

---

## 2. Design / DRY / abstraction

### ✅ D1 — `DatabaseSupport` fat interface + giant switch — *fixed in #426*
Interface slimmed to `getDataGenerator(...)`; `AbstractDatabaseSupport` dispatches via an `EnumMap<JDBCType, GeneratorFactory>` seeded with defaults + a `configure(Map)` hook. The ~12 `UnsupportedOperationException` stubs are gone. **Breaking change** to the extension API. `DatabaseSupportTest` added.

### ⬜ D2 — empty marker subclasses (`PostgresSupport`, `MySQLSupport`, `DefaultSupport`)
Still behaviorally identical to `DefaultSupport`. They now at least serve as auto-selection targets (D3), but carry no DB-specific generation. Add real behavior or document the no-op honestly.

### ✅ D3 — no product→support selection — *fixed in #427*
Added `DatabaseSupport.forProduct(String)` and `forConnection(Connection)`. CockroachDB-reports-as-PostgreSQL caveat is documented and tested.

### ⬜ D4 — CockroachDB type dispatch is fragile string-matching
Still matches driver catalog names (`"_int8"`, `"varbit"`, `"jsonb"`, …) with hardcoded literals (now inside registry entries rather than overridden methods). Consider a shared constant name→generator map.

### 🔧 D5 — generator builder boilerplate
The per-call `SeededRandomUtils` allocation was removed (D7, #425), but the repeated `Builder`/`build()`/private-ctor and `start`/`end` range scaffolding remains. A generic `RangeBuilder` would change the public `start`/`end` signatures (type-divergent across int/long/double/float), so it was deferred as an API-affecting change.

### ⬜ D6 — `FileDefinition` hierarchy is redundant
`CsvFile`/`TabDelimitedFile`/`PipeDelimitedFile` could collapse into `FileType`. Deferred — removing the public classes is API-breaking.

### ✅ D7 — per-row `SeededRandomUtils` allocation — *fixed in #425*
A single reusable instance now lives on `AbstractDataGenerator`; 13 generators updated.

### ⬜ D8 — `SeededRandomUtils` forbids negative ranges; duplicated validation
`startInclusive >= 0` still blocks negative values, and the four range methods still repeat the same two `Validate` checks. Extract a `validateRange` helper and decide whether to allow negatives.

---

## 3. Modern Java (target: 25)

- ✅ The `AbstractDatabaseSupport` dispatch `switch` is gone (replaced by the registry, #426).
- ⬜ Prefer `Path.of(...)` over `Paths.get(...)` in `FlatFileGenerator`.
- ⬜ `DataGenerator` could be a `sealed` hierarchy (closed implementor set + Jackson `@JsonTypeInfo`).
- ⬜ `AbstractDataGenerator.logger` is a non-static package-private field, mostly unused.
- ⬜ TINYINT → `[0,255]` assumes unsigned; standard JDBC TINYINT is signed `byte`.

---

## 4. Tests

- ✅ Core logic now has fast, container-free coverage: `AssociatedPrimaryKeyColumnTest` (FK-chain resolution) and `DatabaseFillerOrderTest` (topological fill order) in #424; `DatabaseSupportTest` (registry dispatch) in #426; `DatabaseSupportSelectionTest` (product selection) in #427; expanded `ResultSetGetterTest` in #428. To make ordering testable, `buildReversedDependencyGraph()`/`fillOrder()` were extracted from `DatabaseFiller.fill()`.
- ⬜ Each DB integration test still spins up a fresh container per `@Test` — adopt a shared/`@Container` singleton (or `withReuse(true)`).
- ⬜ `FlatFileTest` still asserts nothing and writes into `target/` — give it real assertions over a `@TempDir`.
- ⬜ Heavy duplication across the three `Base*Test` and `*FillerTest` classes — candidates for `@ParameterizedTest`.
- ⬜ Some generator tests use unseeded `new Random()` for regression assertions — fix a seed.

---

## 5. Dead code & docs

- ✅ Deleted `ScriptRunner` (and dropped the unused `commons-compress` dep), the no-op `FlatFileGenerator.compress` feature, the unused `FileType` delimiter, `DatabaseUtils.getExportedKeys`, and the dead `StringArrayGenerator.Builder.elementGenerator` field. *(#424)*
- ✅ `getColumn` now throws `IllegalStateException` instead of a raw `RuntimeException`. *(#424)*
- ✅ README badge/requirements and `CLAUDE.md` updated from Java 16 to Java 25. *(#424)*
- ⬜ `SqlBlobGenerator`/`SqlClobGenerator` stubs remain undocumented (see C5).

---

## Remaining recommendations (in rough priority order)

1. **C7** — stop swallowing write failures in `FlatFileGenerator` (small, real correctness).
2. **C5** — implement or clearly document the BLOB/CLOB stubs.
3. **Test infra** — shared container lifecycle + real `FlatFileTest` assertions + `@ParameterizedTest` de-duplication.
4. **D6 / D5** — `FileDefinition`→`FileType` collapse and a numeric `RangeBuilder` (both API-breaking; bundle into a deliberate breaking release).
5. **Polish** — D2 (honest support subclasses), D4 (CRDB name→generator map), D8 (negative ranges + validation helper), and the modern-Java items.
