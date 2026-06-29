# Generators

Bloviate picks a generator for every column from its JDBC type by default. When you want to apply a
custom generator **broadly** — across many columns or whole types — or generate realistic,
referentially-consistent data, reach for the registry, the Datafaker module, and the key/cardinality
generators described here.

## Custom generator registry

Per-column overrides are precise but must be wired one column at a time. When you want a custom
generator applied **broadly** — every column named `email`, every `uuid` vendor type, or every
`INTEGER` — register it once on a `GeneratorRegistry` and attach it to the `DatabaseConfiguration`.
No subclassing of `DatabaseSupport` required.

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
`META-INF/services/io.bloviate.ext.GeneratorPlugin`. Calling `.discover()` on the builder loads
every such plugin via `ServiceLoader`:

```java
public class MyGenerators implements GeneratorPlugin {
    @Override
    public void contribute(GeneratorRegistry.Builder builder) {
        builder.registerTypeName("geometry",
            (column, random) -> new MyGeometryGenerator.Builder(random).build());
    }
}
```

## Semantic / realistic data (`bloviate-datafaker`)

The type-driven default fills an `email VARCHAR` from its type (random text). The optional
**`bloviate-datafaker`** module maps common column **names** (`email`, `first_name`, `phone`,
`city`, `zip`, …) to realistic [Datafaker](https://www.datafaker.net/) values — keeping the core
dependency-free (Datafaker only lands if you add this module).

It's **opt-in twice over**: add the module, and build a `GeneratorRegistry` that includes its plugin
— either by `discover()` (it registers via `ServiceLoader`) or explicitly:

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

- **Reproducible** — realistic values are seeded from the engine's column seed, so the same seed
  gives the same data; the locale is fixed (default `Locale.ENGLISH`) so output is identical across
  machines.
- **Safe by default** — reserved `example.*` email domains and `555-01xx` phone numbers (never real
  identifiers); values are truncated to the column's length.
- **Unmatched columns are untouched** — anything the dictionary doesn't recognize falls through to
  the normal type-based generator. Don't route `UNIQUE`/primary-key columns through it — realistic
  dictionaries repeat at scale, so keep Bloviate's sequence/seeded generators for keys.

## Correlated columns (referential realism)

The plugin above makes each column realistic *independently* — so a row's `email` won't match its
`first_name`/`last_name`. For columns that should agree, share one **`RowContext`** and register
each as a **projection** of the same per-row entity. `People.context(...)` provides a coherent
person whose `email`, `username`, and `full_name` are derived from the row's name:

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
entity is a pure function of `(seed, rowIndex)`, so the data is **reproducible and
order-independent** — sequential and parallel/partitioned fills produce identical values. Email and
username carry a row-index suffix so they stay unique at scale.

The same mechanism gives **consistent geo tuples**: `Places.unitedStates(...)` draws one real place
per row from a bundled reference dataset, so a row's `city`, `state`, `zip`, and `area_code` agree
(a real city in its real state with a valid local ZIP — not "Springfield, WY 90210"):

```java
import io.bloviate.datafaker.Geo;
import io.bloviate.datafaker.Places;

RowContext<Geo> place = Places.unitedStates(42L);

Set<ColumnConfiguration> columns = Set.of(
    new ColumnConfiguration("city",      place.project(Geo::city)),
    new ColumnConfiguration("state",     place.project(Geo::stateAbbreviation)),
    new ColumnConfiguration("zip",       place.project(Geo::zip)),
    new ColumnConfiguration("area_code", place.project(Geo::areaCode))
);
```

> The bundled geo dataset is a representative sample of US places, not exhaustive, and tuples repeat
> across rows — keep `UNIQUE` columns on Bloviate's sequence/seeded generators.

## Composite keys & foreign key fidelity

For schemas with composite primary keys, `CompositeKeyComponentGenerator` fills each key component
as one dimension of a dense, row-ordered **cartesian product**. Each component emits
`start + ((rowNumber / repeat) % cycle)`, so a table receives exactly its cartesian cardinality of
rows with unique, collision-free keys — and a child table reusing the *same* formula produces
foreign keys that line up exactly with its parent's keys.

For example, a `stock` table keyed by `(s_w_id, s_i_id)` across 2 warehouses × 10 items (20 rows) —
`repeat` for the outer dimension is the size of the inner one:

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

## Variable parent/child cardinality

Real schemas rarely have a fixed number of children per parent (an order has *some* number of line
items). `ChildCardinality` assigns each parent a deterministic child count in `[min, max]`, and the
matching generators keep a parent table and its child table perfectly in sync — no shared mutable
state, O(1) memory, reproducible from a seed.

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

## TPC-C benchmark data

Bloviate ships a ready-made configuration for the full **TPC-C** schema. A single call wires up all
nine tables with spec-faithful cardinalities, composite keys, foreign-key fidelity, variable
order-line counts, per-district customer-id permutations, deterministic last-name enumeration, and
delivery state — matching the TPC-C initial database population (clause 4.3.3.1).

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

The matching `create_tpcc.{postgres,mysql,cockroachdb}.sql` DDL is available in the
[Bloviate repository](https://github.com/timveil/bloviate). The reusable
building blocks behind `TPCCConfiguration` — `CompositeKeyComponentGenerator`, `ChildCardinality`,
`GroupedPermutationGenerator` (per-group shuffles), and `GroupedPrefixGenerator` (nullable/sparse
columns) — live in `io.bloviate.gen` and can be composed for other benchmark schemas (TPC-H,
TPC-DS, and so on).

## Data generator types

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

Additional generators cover `Long`, `Float`, `Byte`, `Short`, `Character`, `java.sql` date/time
types, arrays, and CockroachDB-specific types (bit strings, intervals). See the `io.bloviate.gen`
package for the full set.
