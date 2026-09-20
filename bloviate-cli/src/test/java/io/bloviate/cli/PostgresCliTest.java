/*
 * Copyright (c) 2021 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.cli;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end against a real PostgreSQL, through the same entry point the jar uses (minus
 * {@code System.exit}): the scenario the CLI exists for, a schema whose detail tables are filled and
 * whose derived table is computed from them afterwards.
 *
 * <p>One container serves the tests that only need somewhere to fill; each of those creates its own
 * schema (and fills it with {@code --schema}), so they cannot see each other's rows. The seed
 * reproducibility test starts two containers of its own, because the schema and database names are part
 * of every column's seed, so "fresh database" has to mean the same names.
 */
class PostgresCliTest {

    /** The scenario schema. {@code %1$s} is the schema name. */
    private static final String DDL = """
            create schema if not exists %1$s;
            create table %1$s.customers (id integer primary key, name varchar(40) not null, region varchar(10) not null);
            create table %1$s.orders (id integer primary key, customer_id integer not null references %1$s.customers (id),
                amount integer not null);
            create table %1$s.order_lines (id integer primary key, order_id integer not null references %1$s.orders (id),
                qty integer not null, price numeric(10, 2) not null);
            create table %1$s.order_stats (region varchar(10) primary key, line_count bigint not null, qty_total bigint not null);
            """;

    private static final String TRUNCATE = "truncate order_lines, orders, customers, order_stats;";

    private static final String ROLLUP = """
            insert into order_stats
            select c.region, count(*), sum(l.qty)
            from order_lines l join orders o on o.id = l.order_id join customers c on c.id = o.customer_id
            group by c.region;
            """;

    private static PostgreSQLContainer<?> postgres;

    @TempDir
    Path directory;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>(TestImages.POSTGRES);
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        postgres.stop();
    }

    private static Map<String, String> passwordFor(PostgreSQLContainer<?> container) {
        return Map.of(FillOptions.PASSWORD_ENV, container.getPassword());
    }

    private static CliRun fill(PostgreSQLContainer<?> container, String... extra) {
        String[] args = new String[extra.length + 2];
        args[0] = "--user";
        args[1] = container.getUsername();
        System.arraycopy(extra, 0, args, 2, extra.length);
        return CliRun.fill(passwordFor(container), container.getJdbcUrl(), args);
    }

    private static String newSchema() throws SQLException {
        String schema = "s_" + UUID.randomUUID().toString().replace("-", "");
        execute(postgres, DDL.formatted(schema));
        return schema;
    }

    private static void execute(PostgreSQLContainer<?> container, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> rows(PostgreSQLContainer<?> container, String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                StringBuilder row = new StringBuilder();
                for (int column = 1; column <= metaData.getColumnCount(); column++) {
                    row.append(column == 1 ? "" : "|").append(resultSet.getString(column));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    private static long scalar(String sql) throws SQLException {
        return Long.parseLong(rows(postgres, sql).getFirst());
    }

    private static long count(String schema, String table) throws SQLException {
        return scalar("select count(*) from " + schema + "." + table);
    }

    private Path script(String name, String sql) throws IOException {
        return Files.writeString(directory.resolve(name), sql);
    }

    @Test
    void detailTablesAreFilledAndTheDerivedTableIsComputedByAnAfterScript() throws SQLException, IOException {
        String schema = newSchema();
        Path before = script("truncate.sql", TRUNCATE);
        Path after = script("rollup.sql", ROLLUP);

        for (int attempt = 1; attempt <= 2; attempt++) {
            // the second run only works because the before script empties what the first one filled
            // every row count is stated: a child with more rows than a parent that has no table
            // configuration of its own would reference parent keys that were never generated
            CliRun run = fill(postgres, "--schema", schema, "--exclude", "order_stats", "--before", before.toString(),
                    "--after", after.toString(), "--table-rows", "customers=200,orders=200,order_lines=600",
                    "--threads", "3", "--seed", "11");

            assertEquals(0, run.code(), "run " + attempt + ": " + run.err());
            assertEquals(200, count(schema, "customers"));
            assertEquals(200, count(schema, "orders"));
            assertEquals(600, count(schema, "order_lines"));
            assertEquals(0, scalar("select count(*) from %1$s.order_lines l left join %1$s.orders o on o.id = l.order_id "
                    .formatted(schema) + "where o.id is null"));

            // the rollup was computed from the generated detail rows, so it cannot disagree with them
            assertEquals(600, scalar("select sum(line_count) from " + schema + ".order_stats"));
            assertEquals(scalar("select sum(qty) from " + schema + ".order_lines"),
                    scalar("select sum(qty_total) from " + schema + ".order_stats"));
            assertEquals(scalar("select count(distinct c.region) from %1$s.customers c join %1$s.orders o on o.customer_id = c.id "
                    .formatted(schema) + "join " + schema + ".order_lines l on l.order_id = o.id"),
                    count(schema, "order_stats"));
        }
    }

    @Test
    void schemaScopesTheFillAndIncludeExcludeChooseTheTables() throws SQLException {
        String filled = newSchema();
        String untouched = newSchema();

        CliRun run = fill(postgres, "--schema", filled, "--include", "cust*,order*", "--exclude", "*_stats", "--rows", "15");

        assertEquals(0, run.code(), run.err());
        assertEquals(15, count(filled, "customers"));
        assertEquals(15, count(filled, "orders"));
        assertEquals(15, count(filled, "order_lines"));
        assertEquals(0, count(filled, "order_stats"));
        for (String table : List.of("customers", "orders", "order_lines", "order_stats")) {
            assertEquals(0, count(untouched, table), untouched + "." + table + " must be untouched");
        }
    }

    @Test
    void aSelectionThatCutsAForeignKeyOrMatchesNothingIsAUsageErrorAndWritesNothing() throws SQLException {
        String schema = newSchema();

        CliRun cutsForeignKey = fill(postgres, "--schema", schema, "--exclude", "customers");
        assertEquals(2, cutsForeignKey.code(), cutsForeignKey.err());
        assertTrue(cutsForeignKey.err().contains("customers"), cutsForeignKey.err());

        CliRun matchesNothing = fill(postgres, "--schema", schema, "--include", "no_such_table*");
        assertEquals(2, matchesNothing.code(), matchesNothing.err());

        for (String table : List.of("customers", "orders", "order_lines", "order_stats")) {
            assertEquals(0, count(schema, table), table);
        }
    }

    @Test
    void aFailingAfterScriptIsExitOneAndNothingRunsAfterIt() throws SQLException, IOException {
        String schema = newSchema();
        Path first = script("first.sql", "insert into order_stats values ('first', 1, 1);");
        Path failing = script("failing.sql", "insert into order_stats select 1 / 0, 1, 1;");
        Path never = script("never.sql", "insert into order_stats values ('never', 1, 1);");

        CliRun run = fill(postgres, "--schema", schema, "--exclude", "order_stats", "--rows", "10", "--after", first.toString(),
                "--after", failing.toString(), "--after", never.toString());

        assertEquals(1, run.code());
        assertTrue(run.err().startsWith("bloviate: fill failed: "), run.err());
        assertTrue(run.err().contains("failing.sql"), run.err());
        assertEquals(List.of("first"), rows(postgres, "select region from " + schema + ".order_stats"));
        assertEquals(10, count(schema, "customers"), "the tables filled before the failure stay filled");
    }

    @Test
    void aFailedInsertNamesTheTableAndTheServersReason() throws SQLException {
        String schema = newSchema();
        assertEquals(0, fill(postgres, "--schema", schema, "--exclude", "order_stats", "--rows", "10").code());

        // a fill is not idempotent: the second run collides on the primary key of the first table
        CliRun again = fill(postgres, "--schema", schema, "--exclude", "order_stats", "--rows", "10");

        assertEquals(1, again.code());
        assertTrue(again.err().startsWith("bloviate: fill failed: failed to fill table [customers]: "), again.err());
        assertTrue(again.err().contains("duplicate key value violates unique constraint"), again.err());
        assertEquals(1, again.err().lines().count(), again.err());
        assertTrue(again.err().length() < 1000, "a long batch statement is shortened: " + again.err().length());
    }

    @Test
    void aWrongPasswordIsAConnectionErrorAndIsNotEchoed() throws SQLException {
        String schema = newSchema();
        String wrong = "not-the-password-Qw8";

        CliRun run = CliRun.fill(Map.of(FillOptions.PASSWORD_ENV, wrong), postgres.getJdbcUrl(),
                "--user", postgres.getUsername(), "--schema", schema, "-v");

        assertEquals(3, run.code(), run.err());
        assertTrue(run.err().startsWith("bloviate: cannot connect to jdbc:postgresql://"), run.err());
        assertFalse((run.out() + run.err()).contains(wrong), run.err());
        assertEquals(0, count(schema, "customers"));
    }

    @Test
    void theSameSeedGivesIdenticalDataInFreshDatabasesWhateverTheThreadCount() throws SQLException {
        String[] dump = {"select * from customers order by id", "select * from orders order by id",
                "select * from order_lines order by id"};
        List<List<String>> sequential = fillFreshDatabase("--seed", "7", "--rows", "50", "--exclude", "order_stats");
        List<List<String>> parallel = fillFreshDatabase("--seed", "7", "--rows", "50", "--exclude", "order_stats",
                "--threads", "3");
        List<List<String>> otherSeed = fillFreshDatabase("--seed", "8", "--rows", "50", "--exclude", "order_stats");

        assertEquals(dump.length, sequential.size());
        for (int table = 0; table < dump.length; table++) {
            assertEquals(50, sequential.get(table).size(), dump[table]);
            assertEquals(sequential.get(table), parallel.get(table), "same seed: " + dump[table]);
        }
        assertNotEquals(sequential.get(0), otherSeed.get(0), "a different seed must give different data");
    }

    /** Fills a brand new PostgreSQL (same database and schema names every time) and dumps the detail tables. */
    private static List<List<String>> fillFreshDatabase(String... args) throws SQLException {
        try (PostgreSQLContainer<?> fresh = new PostgreSQLContainer<>(TestImages.POSTGRES)) {
            fresh.start();
            execute(fresh, DDL.formatted("public"));

            CliRun run = fill(fresh, args);
            assertEquals(0, run.code(), run.err());

            return List.of(rows(fresh, "select * from customers order by id"),
                    rows(fresh, "select * from orders order by id"),
                    rows(fresh, "select * from order_lines order by id"));
        }
    }
}
