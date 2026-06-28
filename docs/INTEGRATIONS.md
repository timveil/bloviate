# Testing Integrations

Bloviate ships first-class integrations for the two tools test suites reach for most: JUnit 5 and
Testcontainers. Both regenerate reproducible data before your tests and auto-detect the database
support from the connection.

## JUnit 5 (`bloviate-junit`)

Fill a test database declaratively. Annotate the test with `@FillDatabase` and mark the
`DataSource`/`Connection` to fill with `@FillSource`; the data is regenerated before each test,
reproducibly via `seed`. Create the schema first (init script, Flyway/Liquibase, etc.) — Bloviate
fills the tables it discovers.

```java
import io.bloviate.junit.FillDatabase;
import io.bloviate.junit.FillSource;
import javax.sql.DataSource;

@FillDatabase(rows = 250, seed = 42)
class OrderRepositoryTest {

    @FillSource
    static DataSource dataSource = createDataSource(); // schema already created

    @Test
    void queriesGeneratedData() throws Exception {
        // dataSource now holds 250 reproducible rows per table
    }

    @Test
    @FillDatabase(rows = 5, seed = 7) // method-level overrides the class default
    void worksWithASmallDataset() throws Exception {
        // ...
    }
}
```

`@FillDatabase` is meta-annotated with `@ExtendWith(BloviateExtension.class)`, so no separate
`@ExtendWith` is needed. The database support is auto-detected from the connection.

## Testcontainers (`bloviate-testcontainers`)

Fill a started `JdbcDatabaseContainer` in one call — the public form of the pattern Bloviate uses in
its own tests:

```java
import io.bloviate.testcontainers.BloviateContainers;
import org.testcontainers.containers.PostgreSQLContainer;

try (var postgres = new PostgreSQLContainer<>("postgres:18-alpine")
        .withInitScript("schema.sql")) {  // creates the schema on startup
    postgres.start();

    BloviateContainers.forContainer(postgres)
        .rows(500)
        .seed(42)
        .fill();

    // ... assert against the filled database
}
```
