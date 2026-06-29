# File Generation

Bloviate can generate flat files — CSV, TSV, or pipe-delimited — straight from column definitions,
with no database involved. You control every column's generator, the row count, and the output
format.

## A basic CSV

`FlatFileGenerator` takes an output path and a list of `ColumnDefinition`s, each pairing a column
name with a generator. The default output format is CSV:

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

## Other formats

Pass an explicit `output(...)` to switch the delimiter:

```java
import io.bloviate.file.*;

// Tab-delimited file
new FlatFileGenerator.Builder("data/output")
    .output(new TabDelimitedFile())
    .addAll(columnDefinitions)
    .rows(5000)
    .build()
    .generate();

// Pipe-delimited file
new FlatFileGenerator.Builder("data/output")
    .output(new PipeDelimitedFile())
    .addAll(columnDefinitions)
    .rows(5000)
    .build()
    .generate();
```

## Options

- **Output Format**: CSV (default), TSV, or pipe-delimited
- **Row Count**: number of rows to generate (`rows(n)`)
- **Custom Column Definitions**: full control over each column's generator

See [Generators](./GENERATORS.md#data-generator-types) for the full list of generators you can
attach to a column.
