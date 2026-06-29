# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Bloviate is a Java library for generating dummy data for JDBC-compatible relational databases. It automatically fills database tables with appropriate data based on detected column types and relationships, and can also generate flat files (CSV, TSV, pipe-delimited).

It is a multi-module Maven build:
- `bloviate-core` — the dependency-free engine (all core packages below live here)
- `bloviate-junit` — JUnit Jupiter integration (`@FillDatabase`/`@FillSource`, `BloviateExtension`); JUnit is a `provided` dependency
- `bloviate-testcontainers` — fills a started `JdbcDatabaseContainer` (`BloviateContainers`); Testcontainers is a `provided` dependency

The root `pom.xml` is the parent (packaging `pom`) and centralizes dependency/plugin versions.

## Build System and Commands

This is a Maven-based Java 25 project. Run `./mvnw` from the repository root to build the whole reactor. Common commands:

```bash
# Build the project
./mvnw compile

# Run tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=PostgresFillerTest

# Clean and compile
./mvnw clean compile

# Package the project
./mvnw package
```

## Architecture Overview

### Core Components

1. **Database Layer (`io.bloviate.db`)**:
   - `DatabaseFiller`: Main entry point that orchestrates the filling process using topological sorting to handle foreign key dependencies
   - `Database`, `Table`, `Column`: Metadata models representing database structure
   - `TableFiller`: Handles filling individual tables with generated data

2. **Database Support (`io.bloviate.ext`)**:
   - Database-specific implementations: `PostgresSupport`, `MySQLSupport`, `CockroachDBSupport`, `DefaultSupport`
   - Each provides database-specific SQL generation and data type mapping

3. **Data Generators (`io.bloviate.gen`)**:
   - Type-specific generators for all common database types (String, Integer, Date, UUID, etc.)
   - Each generator follows the Builder pattern and supports seeded random generation

4. **File Generation (`io.bloviate.file`)**:
   - `FlatFileGenerator`: Creates CSV, TSV, or pipe-delimited files
   - `ColumnDefinition`: Defines columns for flat file output
   - File format support through `FileDefinition` implementations

### Key Design Patterns

- **Builder Pattern**: Used extensively for `DatabaseFiller`, `FlatFileGenerator`, and all data generators
- **Strategy Pattern**: `DatabaseSupport` implementations provide database-specific behavior
- **Graph Processing**: Uses JGraphT library to build dependency graphs and perform topological sorting for table fill order

### Dependencies and Foreign Keys

The system automatically:
1. Builds a directed graph of table dependencies based on foreign keys
2. Uses topological sorting to determine proper fill order
3. Generates DOT notation graphs for visualization (logged with GraphvizOnline links)

## Testing

Tests use TestContainers for integration testing with real databases:
- `BaseDatabaseTestCase`: Base class providing DataSource creation
- `PostgresFillerTest`, `MySqlFillerTest`, `CockroachDBFillerTest`: Database-specific integration tests
- Test SQL schemas in `bloviate-core/src/test/resources/` (TPCC, AuctionMark, Wikipedia, etc.)

## Usage Patterns

### Database Filling
```java
new DatabaseFiller.Builder(connection,
    new DatabaseConfiguration(batchSize, recordCount, databaseSupport, tableConfigs))
    .build()
    .fill();
```

### File Generation
```java
new FlatFileGenerator.Builder("output-file")
    .addAll(columnDefinitions)
    .rows(1000)
    .build()
    .generate();
```

## Databases for Testing

Integration tests use Testcontainers, so a running Docker daemon is the only
prerequisite — containers are started and torn down automatically. There are no
manually-managed database instances.