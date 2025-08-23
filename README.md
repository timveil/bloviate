# Bloviate

[![Java CI with Maven](https://github.com/timveil/bloviate/actions/workflows/maven.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/maven.yml)
[![CodeQL](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/timveil/bloviate/actions/workflows/codeql-analysis.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-16+-orange.svg)](https://openjdk.java.net/)

A powerful Java library for generating realistic test data for JDBC-compatible relational databases and flat files. Bloviate automatically analyzes your database schema and generates appropriate data while respecting foreign key relationships and constraints.

## 🚀 Features

- **Automatic Schema Discovery**: Connects to existing databases and analyzes table structure, relationships, and constraints
- **Smart Data Generation**: Generates appropriate data based on column types and database-specific features  
- **Foreign Key Support**: Handles complex table dependencies using topological sorting
- **Multiple Database Support**: PostgreSQL, MySQL, CockroachDB with extensible architecture
- **Flat File Generation**: Export data to CSV, TSV, and pipe-delimited formats
- **Configurable Output**: Control batch sizes, record counts, and custom data generation rules
- **Graph Visualization**: Generate DOT notation graphs of table relationships

## 📋 Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Database Support](#database-support)
- [Usage Examples](#usage-examples)
- [Configuration](#configuration)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## 📦 Installation

### Maven

```xml
<dependency>
    <groupId>io.bloviate</groupId>
    <artifactId>bloviate-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Requirements

- Java 16 or higher
- JDBC-compatible database (for database filling)

## 🚀 Quick Start

### Database Filling

Fill an existing database with generated test data:

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

### Flat File Generation

Generate CSV files with custom column definitions:

```java
import io.bloviate.file.FlatFileGenerator;
import io.bloviate.file.ColumnDefinition;
import io.bloviate.gen.*;

Random random = new Random();
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

## 🗄️ Database Support

| Database | Support Class | Status |
|----------|---------------|--------|
| PostgreSQL | `PostgresSupport` | ✅ Full Support |
| MySQL | `MySQLSupport` | ✅ Full Support |
| CockroachDB | `CockroachDBSupport` | ✅ Full Support |
| Generic JDBC | `DefaultSupport` | ⚠️ Basic Support |

## 📖 Usage Examples

### Advanced Database Configuration

```java
import io.bloviate.db.*;

// Custom table configuration
Map<String, TableConfiguration> tableConfigs = new HashMap<>();
tableConfigs.put("users", new TableConfiguration(50)); // 50 records for users table

DatabaseConfiguration config = new DatabaseConfiguration(
    10,                    // batch size
    100,                   // default records per table
    new PostgresSupport(), // database support
    tableConfigs          // table-specific overrides
);

DatabaseFiller filler = new DatabaseFiller.Builder(connection, config)
    .build();
filler.fill();
```

### Different File Formats

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

### Data Generator Types

Bloviate includes generators for all common database types:

```java
// Numeric generators
new IntegerGenerator.Builder(random).min(1).max(1000).build()
new DoubleGenerator.Builder(random).min(0.0).max(100.0).build()
new BigDecimalGenerator.Builder(random).precision(10).scale(2).build()

// String generators
new SimpleStringGenerator.Builder(random).minLength(5).maxLength(50).build()
new UUIDGenerator.Builder(random).build()

// Date/Time generators
new DateGenerator.Builder(random).build()
new SqlTimestampGenerator.Builder(random).build()
new InstantGenerator.Builder(random).build()

// Boolean and specialized generators
new BooleanGenerator.Builder(random).build()
new JsonbGenerator.Builder(random).build()
new InetGenerator.Builder(random).build()
```

## ⚙️ Configuration

### Database Configuration Options

- **Batch Size**: Number of records inserted in each batch operation
- **Record Count**: Default number of records to generate per table
- **Database Support**: Database-specific implementation for optimal compatibility
- **Table Configurations**: Override settings for specific tables

### File Generation Options

- **Output Format**: CSV, TSV, or pipe-delimited
- **Row Count**: Number of rows to generate
- **Compression**: Optional file compression
- **Custom Column Definitions**: Full control over data generation

## 🛠️ Development

### Building the Project

```bash
# Compile the project
./mvnw compile

# Run tests
./mvnw test

# Package the JAR
./mvnw package
```

### Running Tests

The project uses TestContainers for integration testing with real databases:

```bash
# Run all tests
./mvnw test

# Run specific database tests
./mvnw test -Dtest=PostgresFillerTest
./mvnw test -Dtest=MySqlFillerTest
./mvnw test -Dtest=CockroachDBFillerTest
```

### Docker Support

Start local database instances for development:

```bash
# PostgreSQL
cd docker/postgres && ./up.sh

# MySQL  
cd docker/mysql && ./up.sh

# CockroachDB
cd docker/crdb && ./up.sh
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow existing code style and conventions
- Add tests for new features
- Update documentation as needed
- Ensure all tests pass before submitting PR

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙋‍♂️ Support

- **Issues**: [GitHub Issues](https://github.com/timveil/bloviate/issues)
- **Discussions**: [GitHub Discussions](https://github.com/timveil/bloviate/discussions)
- **Email**: tjveil@gmail.com

## 📈 Roadmap

- [ ] Additional database support (Oracle, SQL Server)
- [ ] Custom data generation plugins
- [ ] Performance optimizations for large datasets
- [ ] GUI for configuration management
- [ ] Integration with popular testing frameworks

---

<div align="center">
Made with ❤️ by <a href="https://github.com/timveil">Tim Veil</a>
</div>