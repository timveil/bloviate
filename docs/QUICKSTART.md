# Quick Start

Bloviate fills an existing JDBC database — or a flat file — with realistic, reproducible test
data. It auto-discovers your schema, respects foreign keys, and generates appropriate values for
each column type. This page gets you from zero to a filled database (or file) in a few minutes.

## Requirements

- Java 25 or higher
- A JDBC-compatible database (for database filling)

## Installation

Add `bloviate-core` to your build. It is the dependency-free engine — `DatabaseFiller`, the
generators, and flat-file generation.

```xml
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-core</artifactId>
    <version>LATEST</version>
</dependency>
```

Bloviate is a multi-module build. The optional integration modules pull in their framework as a
`provided` dependency, so you bring your own version of JUnit / Testcontainers:

| Artifact | Use it for |
| --- | --- |
| `bloviate-core` | The data-generation engine (`DatabaseFiller`, generators, flat files) |
| `bloviate-junit` | JUnit Jupiter: fill a test database declaratively with `@FillDatabase` |
| `bloviate-testcontainers` | Fill a started `JdbcDatabaseContainer` in one call |
| `bloviate-datafaker` | Optional realistic values by column name (email, name, address …) via Datafaker |

```xml
<!-- test-scoped: JUnit 5 integration -->
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-junit</artifactId>
    <version>LATEST</version>
    <scope>test</scope>
</dependency>

<!-- test-scoped: Testcontainers integration -->
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-testcontainers</artifactId>
    <version>LATEST</version>
    <scope>test</scope>
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

## Fill a database

Point Bloviate at a connection and let it fill every table it discovers:

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

That's the whole flow: Bloviate introspects the schema, orders the tables by their foreign keys,
and inserts 100 rows into each. From here you can override row counts and individual columns — see
the [Configuration](./CONFIGURATION.md) guide — or let it pick the database support automatically,
covered in [Database Support](./DATABASE_SUPPORT.md).

## Generate a flat file

No database required — generate a CSV (or TSV / pipe-delimited) file from explicit column
definitions:

```java
import io.bloviate.file.FlatFileGenerator;
import io.bloviate.file.ColumnDefinition;
import io.bloviate.gen.*;
import io.bloviate.util.RandomGenerators;
import java.util.random.RandomGenerator;

RandomGenerator random = RandomGenerators.create(42L);
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

See [File Generation](./FILE_GENERATION.md) for the other formats and options.

## Next steps

- [Database Support](./DATABASE_SUPPORT.md) — which databases and vendor types are covered.
- [Configuration](./CONFIGURATION.md) — per-table and per-column control, distributions, seeds,
  parallelism, and commit strategy.
- [Generators](./GENERATORS.md) — the registry, realistic semantic data, composite keys, and TPC-C.
- [Testing Integrations](./INTEGRATIONS.md) — JUnit 5 and Testcontainers.
