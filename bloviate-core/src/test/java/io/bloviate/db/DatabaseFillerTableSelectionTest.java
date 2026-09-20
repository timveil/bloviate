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

package io.bloviate.db;

import io.bloviate.ext.H2Support;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests table include/exclude and schema selection on {@link DatabaseFiller} against in-memory H2 (no
 * Docker), on the single-{@link Connection} path and on the {@link javax.sql.DataSource} paths, both
 * sequential and with worker threads.
 *
 * <p>Schema {@code A} holds a {@code detail} table only; schema {@code B} holds a {@code detail} table
 * of the same name and shape, a derived {@code order_stats} table, and a small parent/child pair, so a
 * fill of {@code B} that touched {@code A} (or the reverse) is caught by row counts.
 */
class DatabaseFillerTableSelectionTest {

    private static final int ROWS = 40;

    private static final String SCHEMA = """
            create schema a;
            create schema b;
            create table a.detail (id int primary key, grp int, amount int);
            create table b.detail (id int primary key, grp int, amount int);
            create table b.order_stats (grp int, total bigint, n bigint);
            create table b.customers (id int primary key, name varchar(30));
            create table b.orders (id int primary key, customer_id int, foreign key (customer_id) references b.customers (id));
            create table b.audit_log (id int primary key, note varchar(30));
            """;

    private static final String ROLLUP = """
            insert into order_stats
            select grp, sum(amount), count(*) from detail group by grp;
            """;

    private String url;

    @BeforeEach
    void freshDatabase() throws SQLException {
        url = "jdbc:h2:mem:selection_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", SCHEMA));
        }
    }

    private static DatabaseConfiguration configuration() {
        return new DatabaseConfiguration(16, ROWS, new H2Support(), null, 42L);
    }

    private static DatabaseFiller.Builder filler(Connection connection) {
        return new DatabaseFiller.Builder(connection, configuration());
    }

    private static long count(Connection connection, String table) throws SQLException {
        return scalar(connection, "select count(*) from " + table);
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Every table of schema B and A, so a test can assert exactly which ones were written. */
    private static List<Long> counts(Connection connection) throws SQLException {
        List<Long> counts = new ArrayList<>();
        for (String table : List.of("a.detail", "b.detail", "b.order_stats", "b.customers", "b.orders", "b.audit_log")) {
            counts.add(count(connection, table));
        }
        return counts;
    }

    private static void assertRollupAgrees(Connection connection) throws SQLException {
        assertEquals(scalar(connection, "select sum(amount) from b.detail"), scalar(connection, "select sum(total) from b.order_stats"));
        assertEquals(ROWS, scalar(connection, "select sum(n) from b.order_stats"));
        assertEquals(scalar(connection, "select count(distinct grp) from b.detail"), count(connection, "b.order_stats"));
    }

    // ---- include / exclude -------------------------------------------------------------------

    @Test
    void includeFillsOnlyTheNamedTablesCaseInsensitively() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).includeTables("Detail", "AUDIT_log").build().fill();

            assertEquals(List.of(0L, (long) ROWS, 0L, 0L, 0L, (long) ROWS), counts(connection));
        }
    }

    @Test
    void globPatternsIncludeAndExclude() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            // order_stats is derived, audit_log is not wanted; customers/orders are matched by ?/*
            filler(connection).includeTables("c?stomers", "ORDER*").excludeTables("*_STATS").build().fill();

            assertEquals(List.of(0L, 0L, 0L, (long) ROWS, (long) ROWS, 0L), counts(connection));
        }
    }

    @Test
    void excludeFillsEverythingElse() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).excludeTables("order_stats", "audit_log").build().fill();

            assertEquals(List.of(0L, (long) ROWS, 0L, (long) ROWS, (long) ROWS, 0L), counts(connection));
        }
    }

    @Test
    void includeAndExcludeAreAdditiveAcrossCallsAndCollections() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection)
                    .includeTables("detail").includeTables(List.of("audit_log"))
                    .excludeTables("detail").excludeTables(List.of("nothing_like_this"))
                    .build().fill();

            assertEquals(List.of(0L, 0L, 0L, 0L, 0L, (long) ROWS), counts(connection));
        }
    }

    @Test
    void anIncludePatternThatMatchesNothingFailsNamingItAndWritesNothing() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");
            DatabaseFiller filler = filler(connection).includeTables("detail", "detial").build();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, filler::fill);

            assertTrue(e.getMessage().contains("detial"), e.getMessage());
            assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L), counts(connection));
        }
    }

    @Test
    void anExcludePatternThatMatchesNothingIsNotAnError() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).excludeTables("order_stats", "no_such_table").build().fill();

            assertEquals(ROWS, count(connection, "b.detail"));
            assertEquals(0, count(connection, "b.order_stats"));
        }
    }

    @Test
    void excludingAParentFailsBeforeAnyRowIsWrittenNamingTheChildColumnAndParent() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");
            DatabaseFiller filler = filler(connection).excludeTables("customers").build();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, filler::fill);

            String message = e.getMessage();
            assertTrue(message.contains("[ORDERS]"), message);
            assertTrue(message.contains("CUSTOMER_ID"), message);
            assertTrue(message.contains("[CUSTOMERS]"), message);
            assertTrue(message.contains("includeTables"), "should suggest the way out: " + message);
            assertFalse(message.contains("not found"), "must not be the opaque Database.getTable message: " + message);
            assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L), counts(connection), "not a single row may be written");
        }
    }

    @Test
    void notIncludingAParentFailsTheSameWay() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");
            DatabaseFiller filler = filler(connection).includeTables("orders", "detail").build();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, filler::fill);

            assertTrue(e.getMessage().contains("[CUSTOMERS]"), e.getMessage());
            assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L), counts(connection));
        }
    }

    @Test
    void excludingTheParentTogetherWithItsChildIsFine() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).excludeTables("customers", "orders").build().fill();

            assertEquals(List.of(0L, (long) ROWS, (long) ROWS, 0L, 0L, (long) ROWS), counts(connection));
        }
    }

    @Test
    void excludingALeafTableJustWorksAndLeavesItEmpty() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).excludeTables("orders", "order_stats").build().fill();

            assertEquals(0, count(connection, "b.orders"));
            assertEquals(ROWS, count(connection, "b.customers"), "the parent of an excluded leaf is still filled");
        }
    }

    @Test
    void aSelfReferencingTableIsNotAnExcludedParent() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("tree",
                    "create table b.tree (id int primary key, parent_id int, foreign key (parent_id) references b.tree (id))"));
            connection.setSchema("B");

            // must get past selection validation; the fill itself is the engine's existing business
            DatabaseFiller filler = filler(connection).includeTables("tree").build();
            try {
                filler.fill();
            } catch (SQLException e) {
                // a random self-reference can violate the constraint; not what this test is about
                assertFalse(e.getMessage().contains("not among the tables"), e.getMessage());
            }
        }
    }

    // ---- unused table configurations ------------------------------------------------------------

    @Test
    void tableConfigurationsForUnknownAndExcludedTablesAreReported() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");
            Database database = io.bloviate.util.DatabaseUtils.getMetadata(connection,
                    new TableSelection(List.of(), List.of("order_stats")).asFilter());
            DatabaseConfiguration configuration = new DatabaseConfiguration(16, ROWS, new H2Support(),
                    Set.of(new TableConfiguration("detail", 5), new TableConfiguration("Typo", 5),
                            new TableConfiguration("ORDER_STATS", 0), new TableConfiguration("nope", 1)), 42L);

            DatabaseFiller.UnusedTableConfigurations unused = DatabaseFiller.findUnusedTableConfigurations(database, configuration,
                    new TableSelection(List.of(), List.of("order_stats")));

            assertEquals(List.of("nope", "Typo"), unused.unknown());
            assertEquals(List.of("ORDER_STATS"), unused.excluded());

            // without a selection the same name is simply unknown
            assertEquals(List.of("nope", "ORDER_STATS", "Typo"),
                    DatabaseFiller.findUnusedTableConfigurations(database, configuration, TableSelection.ALL).unknown());
        }
    }

    @Test
    void anUnknownTableConfigurationDoesNotFailTheFill() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");
            DatabaseConfiguration configuration = new DatabaseConfiguration(16, ROWS, new H2Support(),
                    Set.of(new TableConfiguration("no_such_table", 3)), 42L);

            new DatabaseFiller.Builder(connection, configuration).includeTables("detail").build().fill();

            assertEquals(ROWS, count(connection, "b.detail"));
        }
    }

    // ---- schema selection ----------------------------------------------------------------------

    @Test
    void fillsTheSelectedSchemaWhileTheConnectionStaysOnAnother() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("A");

            filler(connection).schema("B").includeTables("detail").build().fill();

            assertEquals(0, count(connection, "a.detail"), "the connection's own schema must be untouched");
            assertEquals(ROWS, count(connection, "b.detail"));
            assertEquals("A", connection.getSchema(), "the caller's schema must be restored");
        }
    }

    @Test
    void theSchemaIsRestoredWhenTheFillFails() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("A");
            DatabaseFiller filler = filler(connection).schema("B").excludeTables("customers")
                    .before(SqlScript.inline("noop", "select 1")).build();

            assertThrows(IllegalArgumentException.class, filler::fill);

            assertEquals("A", connection.getSchema());
        }
    }

    @Test
    void hooksRunInTheSelectedSchemaSoUnqualifiedNamesResolveThere() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("A");

            filler(connection).schema("B").excludeTables("order_stats", "customers", "orders", "audit_log")
                    .before(SqlScript.inline("mark", "delete from detail where id < 0"))
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertRollupAgrees(connection);
            assertEquals("A", connection.getSchema());
        }
    }

    @Test
    void aSchemaThatDoesNotExistFailsClearlyAndLeavesTheConnectionAlone() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("A");
            DatabaseFiller filler = filler(connection).schema("NOPE").build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            assertTrue(e.getMessage().contains("NOPE") || e.getMessage().contains("cannot select schema"), e.getMessage());
            assertEquals("A", connection.getSchema());
        }
    }

    @Test
    void selectingTheCurrentSchemaExplicitlyProducesIdenticalRows() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).includeTables("detail").build().fill();
            String implicit = dump(connection);

            // now from a connection sitting on another schema, so the selection really is applied
            SqlScriptRunner.run(connection, SqlScript.inline("reset", "delete from detail"));
            connection.setSchema("A");
            filler(connection).schema("B").includeTables("detail").build().fill();
            String explicit = dump(connection);

            assertEquals(implicit, explicit);
            assertFalse(implicit.isEmpty());
        }
    }

    @Test
    void theSelectedSchemaAppliesOnASequentialDataSourceWithOneConnection() throws SQLException {
        try (TrackingDataSource dataSource = new TrackingDataSource(url, null, null, 1);
             Connection observer = DriverManager.getConnection(url)) {
            String original = dataSource.physicalConnections().getFirst().getSchema();

            // pool of one: hooks, metadata and fill all share the single held connection
            new DatabaseFiller.Builder(dataSource, configuration())
                    .schema("B").excludeTables("order_stats", "customers", "orders", "audit_log")
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertEquals(0, count(observer, "a.detail"));
            assertRollupAgrees(observer);
            assertEquals(original, dataSource.physicalConnections().getFirst().getSchema(),
                    "the pooled connection must go back with its schema restored");
        }
    }

    @Test
    void theSelectedSchemaAppliesOnEveryWorkerAndPooledConnectionsAreRestored() throws SQLException {
        try (TrackingDataSource dataSource = new TrackingDataSource(url, null, null, 3);
             Connection observer = DriverManager.getConnection(url)) {
            List<String> originals = new ArrayList<>();
            for (Connection physical : dataSource.physicalConnections()) {
                originals.add(physical.getSchema());
            }

            new DatabaseFiller.Builder(dataSource, configuration()).threads(3)
                    .schema("B").excludeTables("order_stats")
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertEquals(0, count(observer, "a.detail"), "no worker may have written to the connection's default schema");
            assertEquals(ROWS, count(observer, "b.customers"));
            assertEquals(ROWS, count(observer, "b.orders"));
            assertEquals(ROWS, count(observer, "b.audit_log"));
            assertRollupAgrees(observer);

            List<String> afters = new ArrayList<>();
            for (Connection physical : dataSource.physicalConnections()) {
                afters.add(physical.getSchema());
            }
            assertEquals(originals, afters, "every pooled connection must be restored");
        }
    }

    @Test
    void excludedParentFailsBeforeAnyRowOnTheParallelPathToo() throws SQLException {
        try (TrackingDataSource dataSource = new TrackingDataSource(url, null, null, 3);
             Connection observer = DriverManager.getConnection(url)) {
            DatabaseFiller filler = new DatabaseFiller.Builder(dataSource, configuration()).threads(3)
                    .schema("B").excludeTables("customers").build();

            assertThrows(IllegalArgumentException.class, filler::fill);

            assertEquals(List.of(0L, 0L, 0L, 0L, 0L, 0L), counts(observer));
            assertEquals("PUBLIC", dataSource.physicalConnections().getFirst().getSchema());
        }
    }

    // ---- the derived-table scenario (#613) -----------------------------------------------------

    @Test
    void excludedDerivedTableIsComputedByAnAfterHookFromTheFilledRows() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).excludeTables("order_stats", "customers", "orders", "audit_log")
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertEquals(ROWS, count(connection, "detail"));
            assertRollupAgrees(connection);
        }
    }

    @Test
    void withoutExclusionTheDerivedTableWouldHaveBeenFilledWithRandomRows() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");

            filler(connection).includeTables("detail", "order_stats").build().fill();

            // documents why exclusion is the fix: the rollup table got ROWS random rows of its own
            assertEquals(ROWS, count(connection, "order_stats"));
        }
    }

    // ---- builder validation --------------------------------------------------------------------

    @Test
    void builderMethodsRejectNullAndBlank() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            DatabaseFiller.Builder builder = filler(connection);

            assertThrows(NullPointerException.class, () -> builder.schema(null));
            assertThrows(NullPointerException.class, () -> builder.catalog(null));
            assertThrows(NullPointerException.class, () -> builder.includeTables((String[]) null));
            assertThrows(NullPointerException.class, () -> builder.includeTables((List<String>) null));
            assertThrows(NullPointerException.class, () -> builder.excludeTables((String[]) null));
            assertThrows(NullPointerException.class, () -> builder.excludeTables((List<String>) null));
            assertThrows(NullPointerException.class, () -> builder.includeTables("ok", null));
            assertThrows(NullPointerException.class, () -> builder.excludeTables("ok", null));
            assertThrows(IllegalArgumentException.class, () -> builder.schema(" "));
            assertThrows(IllegalArgumentException.class, () -> builder.catalog(""));
            assertThrows(IllegalArgumentException.class, () -> builder.includeTables(""));
            assertThrows(IllegalArgumentException.class, () -> builder.excludeTables(List.of(" ")));
        }
    }

    @Test
    void selectionAddedAfterBuildDoesNotLeakIntoTheBuiltFiller() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("B");
            DatabaseFiller.Builder builder = filler(connection).includeTables("detail");
            DatabaseFiller filler = builder.build();

            builder.includeTables("no_such_table").schema("NOPE");

            filler.fill();
            assertEquals(ROWS, count(connection, "b.detail"));
        }
    }

    /** All rows of B.detail in a stable text form, to compare two fills. */
    private static String dump(Connection connection) throws SQLException {
        StringBuilder rows = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select id, grp, amount from b.detail order by id")) {
            while (rs.next()) {
                rows.append(rs.getInt(1)).append('|').append(rs.getInt(2)).append('|').append(rs.getInt(3)).append('\n');
            }
        }
        return rows.toString();
    }
}
