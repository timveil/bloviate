# Bloviate.io

Bloviate is a simple data generator for JDBC compatible, relational databases. It is designed to connect to an empty database and automatically fill tables with the appropriate data given detected column types and relationships.

# Getting Started

Getting started with Bloviate is very simple. All you need to provide is a valid `java.sql.Connection` object and an instance of `io.bloviate.db.DatabaseConfiguration` to the `DatabaseFiller`. The `DatabaseConfiguration` object tells Bloviate about the underlying database type (see `DatabaseSupport`) and provides the ability to define important default values as well as override any `TableConfiguration` data. The following code will:

* Automatically connect to the database using the provided `connection`
* Populate all tables with `10` records using a batch size of `5`
* Assume the underlying databases is an instance of `CockroachDB`
* Provide no custom Table configurations

```java
new DatabaseFiller.Builder(connection,
        new DatabaseConfiguration(5,10,new CockroachDBSupport(),null))
        .build()
        .fill();
```

In addition to populating a database directly, Bloviate can generate various flat file formats. For example, to generate a CSV file you can do the following...

```java
Random random = new Random();

List<ColumnDefinition> definitions = new ArrayList<>();

definitions.add(new ColumnDefinition("integer_col",
        new IntegerGenerator.Builder(random).build()));

definitions.add(new ColumnDefinition("string_col",
        new SimpleStringGenerator.Builder(random).build()));

new FlatFileGenerator.Builder("target/csv-test")
        .addAll(definitions)
        .build()
        .generate();
```

Here we create a `List` of `ColumnDefinition` objects which define the columns in our flat file. In addition to the column definitions, the `FlatFileGenerator` is provided an output path where the generated file will be placed once the `generate()` method completes. By default, `FlatFileGenerator` will create a "comma separated" or CSV file. To generate another type of file, simply pass different `FileDefinition` to the `FlatFileGenerator` via the `.output(...)` method. For example, the
following creates a "tab" delimited file.

```java
new FlatFileGenerator.Builder("target/tab-test")
        .output(new TabDelimitedFile())
        .addAll(definitions)
        .build()
        .generate();
```