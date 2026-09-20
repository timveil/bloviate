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

import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table selection and schema selection against a real PostgreSQL: two schemas ({@code sa}, {@code sb})
 * holding same-named tables, the derived-table scenario end to end, and the schema applied to the
 * single connection, to a one-connection {@link javax.sql.DataSource}, and to every worker of a
 * threaded fill, with the physical connections checked afterwards (through {@link TrackingDataSource},
 * because a real pool would reset the schema itself and hide a leak).
 */
class PostgresTableSelectionTest extends BaseDatabaseTestCase {

    private static final int ROWS = 40;

    private static final String SCHEMA = """
            drop schema if exists sa cascade;
            drop schema if exists sb cascade;
            create schema sa;
            create schema sb;
            create table sa.detail (id int primary key, grp int, amount int);
            create table sb.detail (id int primary key, grp int, amount int);
            create table sb.order_stats (grp int, total bigint, n bigint);
            create table sb.customers (id int primary key, name varchar(30));
            create table sb.orders (id int primary key, customer_id int references sb.customers (id));
            """;

    private static final String ROLLUP = """
            insert into order_stats
            select grp, sum(amount), count(*) from detail group by grp;
            """;

    private static PostgreSQLContainer<?> database;

    @BeforeAll
    static void startDatabase() {
        database = new PostgreSQLContainer<>(TestImages.POSTGRES)
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withUrlParam("stringtype", "unspecified");
        database.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.stop();
    }

    @BeforeEach
    void freshSchemas() throws SQLException {
        try (Connection connection = open()) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", SCHEMA));
        }
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private static TrackingDataSource pool(int size) throws SQLException {
        return new TrackingDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword(), size);
    }

    private static DatabaseConfiguration configuration() {
        return new DatabaseConfiguration(16, ROWS, new PostgresSupport(), null, 42L);
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<Long> counts(Connection connection) throws SQLException {
        List<Long> counts = new ArrayList<>();
        for (String table : List.of("sa.detail", "sb.detail", "sb.order_stats", "sb.customers", "sb.orders")) {
            counts.add(scalar(connection, "select count(*) from " + table));
        }
        return counts;
    }

    private static void assertRollupAgrees(Connection connection) throws SQLException {
        assertEquals(scalar(connection, "select sum(amount) from sb.detail"), scalar(connection, "select sum(total) from sb.order_stats"));
        assertEquals(ROWS, scalar(connection, "select sum(n) from sb.order_stats"));
        assertEquals(scalar(connection, "select count(distinct grp) from sb.detail"), scalar(connection, "select count(*) from sb.order_stats"));
    }

    private static DatabaseFiller.Builder derivedTableFill(DatabaseFiller.Builder builder) {
        // order_stats is derived: keep the fill out of it, and let the hook (unqualified names, so
        // it depends on the schema selection reaching the hook connection) compute it from detail
        return builder.schema("sb").excludeTables("order_stats", "customers", "orders")
                .after(SqlScript.inline("rollup", ROLLUP));
    }

    @Test
    void fillsTheSelectedSchemaWhileTheConnectionStaysOnAnotherAndComputesTheRollup() throws SQLException {
        try (Connection connection = open()) {
            connection.setSchema("sa");

            derivedTableFill(new DatabaseFiller.Builder(connection, configuration())).build().fill();

            assertEquals(List.of(0L, (long) ROWS, scalar(connection, "select count(distinct grp) from sb.detail"), 0L, 0L), counts(connection));
            assertRollupAgrees(connection);
            assertEquals("sa", connection.getSchema(), "the caller's schema must be restored");
            assertTrue(connection.getAutoCommit(), "and nothing else about the connection may change");
        }
    }

    @Test
    void oneConnectionDataSourceRunsHooksAndFillInTheSelectedSchemaAndIsRestored() throws SQLException {
        try (TrackingDataSource dataSource = pool(1); Connection observer = open()) {
            String original = dataSource.physicalConnections().getFirst().getSchema();

            derivedTableFill(new DatabaseFiller.Builder(dataSource, configuration())).build().fill();

            assertEquals(0, scalar(observer, "select count(*) from sa.detail"));
            assertRollupAgrees(observer);
            assertEquals(original, dataSource.physicalConnections().getFirst().getSchema());
        }
    }

    @Test
    void everyWorkerFillsTheSelectedSchemaAndEveryPooledConnectionIsRestored() throws SQLException {
        try (TrackingDataSource dataSource = pool(3); Connection observer = open()) {
            List<String> originals = new ArrayList<>();
            for (Connection physical : dataSource.physicalConnections()) {
                originals.add(physical.getSchema());
            }

            new DatabaseFiller.Builder(dataSource, configuration()).threads(3)
                    .schema("sb").excludeTables("order_stats")
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertEquals(0, scalar(observer, "select count(*) from sa.detail"), "no worker may write to the default schema");
            assertEquals(ROWS, scalar(observer, "select count(*) from sb.customers"));
            assertEquals(ROWS, scalar(observer, "select count(*) from sb.orders"));
            assertRollupAgrees(observer);

            List<String> afters = new ArrayList<>();
            for (Connection physical : dataSource.physicalConnections()) {
                afters.add(physical.getSchema());
            }
            assertEquals(originals, afters);
        }
    }

    @Test
    void unorderedBulkWorkersAlsoUseTheSelectedSchema() throws SQLException {
        try (TrackingDataSource dataSource = pool(3); Connection observer = open()) {
            String original = dataSource.physicalConnections().getFirst().getSchema();
            DatabaseConfiguration bulk = new DatabaseConfiguration(16, ROWS, new PostgresSupport(), null, 42L,
                    null, BulkLoadStrategy.unorderedBulk());

            new DatabaseFiller.Builder(dataSource, bulk).threads(3)
                    .schema("sb").excludeTables("order_stats")
                    .build().fill();

            assertEquals(List.of(0L, (long) ROWS, 0L, (long) ROWS, (long) ROWS), counts(observer));
            for (Connection physical : dataSource.physicalConnections()) {
                assertEquals(original, physical.getSchema());
            }
        }
    }

    @Test
    void excludingAParentFailsBeforeAnyRowIsWritten() throws SQLException {
        try (Connection connection = open()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .schema("sb").excludeTables("customers").build();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, filler::fill);

            assertTrue(e.getMessage().contains("[orders]"), e.getMessage());
            assertTrue(e.getMessage().contains("customer_id"), e.getMessage());
            assertTrue(e.getMessage().contains("[customers]"), e.getMessage());
            assertFalse(e.getMessage().contains("not found"), e.getMessage());
            assertEquals(List.of(0L, 0L, 0L, 0L, 0L), counts(connection));
            assertEquals("public", connection.getSchema());
        }
    }

    @Test
    void aForeignKeyIntoAnotherSchemaIsNotMistakenForTheSameNamedLocalTable() throws SQLException {
        try (Connection connection = open()) {
            // sa gets its own customers table, but its orders reference sb's customers
            SqlScriptRunner.run(connection, SqlScript.inline("cross", """
                    create table sa.customers (id int primary key);
                    create table sa.orders (id int primary key, customer_id int references sb.customers (id));
                    """));
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration()).schema("sa").build();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, filler::fill);

            assertTrue(e.getMessage().contains("[orders]"), e.getMessage());
            assertTrue(e.getMessage().contains("customer_id"), e.getMessage());
            assertTrue(e.getMessage().contains("[customers] in schema [sb]"), e.getMessage());
            assertEquals(0, scalar(connection, "select count(*) from sa.customers"));
            assertEquals(0, scalar(connection, "select count(*) from sa.orders"));
            assertEquals(0, scalar(connection, "select count(*) from sb.customers"));
            assertEquals("public", connection.getSchema());
        }
    }

    @Test
    void aSchemaThatDoesNotExistIsAClearError() throws SQLException {
        try (Connection connection = open()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration()).schema("no_such_schema").build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            assertTrue(e.getMessage().contains("cannot select schema [no_such_schema]"), e.getMessage());
            assertEquals("public", connection.getSchema());
        }
    }

    @Test
    void anUnswitchableCatalogIsAClearError() throws SQLException {
        try (Connection connection = open()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration()).catalog("another_database").build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            assertTrue(e.getMessage().contains("cannot select catalog [another_database]"), e.getMessage());
        }
    }

    @Test
    void selectingTheCurrentSchemaExplicitlyProducesIdenticalRows() throws SQLException {
        try (Connection connection = open()) {
            SqlScriptRunner.run(connection, SqlScript.inline("public", "create table public.detail_seed (id int primary key, grp int, amount int, label varchar(20))"));

            new DatabaseFiller.Builder(connection, configuration()).includeTables("detail_seed").build().fill();
            String implicit = dump(connection);

            SqlScriptRunner.run(connection, SqlScript.inline("reset", "truncate table public.detail_seed"));
            connection.setSchema("sa");
            new DatabaseFiller.Builder(connection, configuration()).schema("public").includeTables("detail_seed").build().fill();
            String explicit = dump(connection);

            assertEquals(implicit, explicit);
            assertFalse(implicit.isEmpty());
            assertEquals("sa", connection.getSchema());
        }
    }

    // ---- caller connection with autocommit OFF: setSchema joins the caller's open transaction ----

    /** A caller connection on schema sa with autocommit off, its own schema choice already committed. */
    private static Connection manualCommitConnectionOnSa() throws SQLException {
        Connection connection = open();
        connection.setSchema("sa");
        connection.setAutoCommit(false);
        connection.commit();
        return connection;
    }

    @Test
    void manualCommitSuccessfulFillLandsInTheSelectedSchemaAndLeavesTheTransactionOpenAndUsable() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa(); Connection observer = open()) {
            new DatabaseFiller.Builder(connection, configuration()).schema("sb").includeTables("detail").build().fill();

            // the fill's rows went to sb, not to the connection's schema, and are still uncommitted
            assertEquals(0, scalar(observer, "select count(*) from sb.detail"), "the caller has not committed yet");
            assertEquals(ROWS, scalar(connection, "select count(*) from sb.detail"));
            assertEquals(0, scalar(connection, "select count(*) from sa.detail"));

            // schema restored, transaction still open and the caller's
            assertEquals("sa", connection.getSchema());
            assertFalse(connection.getAutoCommit());
            assertEquals(0, scalar(connection, "select count(*) from detail"), "unqualified names resolve in sa again");

            connection.commit();

            assertEquals(ROWS, scalar(observer, "select count(*) from sb.detail"));
            assertEquals("sa", connection.getSchema(), "a commit must not bring the selected schema back");
            assertFalse(connection.getAutoCommit());
        }
    }

    @Test
    void manualCommitRollbackAfterASuccessfulFillDiscardsTheRowsAndKeepsTheOriginalSchema() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa(); Connection observer = open()) {
            new DatabaseFiller.Builder(connection, configuration()).schema("sb").includeTables("detail").build().fill();

            // the schema setting and its restore were both inside the transaction, so a rollback
            // undoes both and the connection is back on the schema it had when the transaction began
            connection.rollback();

            assertEquals("sa", connection.getSchema());
            assertEquals(0, scalar(connection, "select count(*) from sb.detail"));
            assertEquals(0, scalar(observer, "select count(*) from sb.detail"));
            assertFalse(connection.getAutoCommit());
        }
    }

    @Test
    void manualCommitHooksRunInTheSelectedSchemaAndTheirCommitKeepsTheRestoreCorrect() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa(); Connection observer = open()) {
            derivedTableFill(new DatabaseFiller.Builder(connection, configuration()))
                    .before(SqlScript.inline("noop", "select 1")).build().fill();

            // the after hook commits the transaction, fill rows included, and does so in schema sb
            assertRollupAgrees(observer);
            assertEquals("sa", connection.getSchema());
            assertFalse(connection.getAutoCommit());
            connection.rollback();
            assertEquals("sa", connection.getSchema());
            assertRollupAgrees(observer);
        }
    }

    @Test
    void manualCommitBeforeHookCommitsTheSelectionButARollbackStillEndsOnTheOriginalSchema() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa(); Connection observer = open()) {
            new DatabaseFiller.Builder(connection, configuration()).schema("sb").includeTables("detail")
                    .before(SqlScript.inline("noop", "select 1")).build().fill();

            // the hook's commit made the selection durable; the fill's rows are still pending
            assertEquals(0, scalar(observer, "select count(*) from sb.detail"));
            assertEquals("sa", connection.getSchema());

            connection.rollback();

            assertEquals("sa", connection.getSchema(), "the restore must not have been lost with the rolled-back rows");
            assertEquals(0, scalar(connection, "select count(*) from sb.detail"));
        }
    }

    @Test
    void manualCommitEngineManagedCommitsMakeTheSelectionDurableSoTheRestoreIsCommittedToo() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa(); Connection observer = open()) {
            DatabaseConfiguration perTable = new DatabaseConfiguration(16, ROWS, new PostgresSupport(), null, 42L, CommitStrategy.perTable());
            new DatabaseFiller.Builder(connection, perTable).schema("sb").includeTables("detail").build().fill();

            assertEquals(ROWS, scalar(observer, "select count(*) from sb.detail"), "committed by the engine");
            assertEquals("sa", connection.getSchema());
            assertFalse(connection.getAutoCommit());

            connection.rollback();

            assertEquals("sa", connection.getSchema());
            assertEquals(ROWS, scalar(connection, "select count(*) from sb.detail"));
        }
    }

    @Test
    void manualCommitAHookFailingAfterAnEarlierOneCommittedStillEndsOnTheOriginalSchema() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration()).schema("sb")
                    .before(SqlScript.inline("good", "select 1"))
                    .before(SqlScript.inline("bad", "select * from no_such_table"))
                    .build();

            assertThrows(SQLException.class, filler::fill);
            connection.rollback();

            assertEquals("sa", connection.getSchema(), "the first hook committed the selection; the restore had to survive a rollback");
        }
    }

    @Test
    void manualCommitFailingBeforeHookLeavesTheOriginalSchemaAndAutocommitOff() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .schema("sb").before(SqlScript.inline("bad", "select * from no_such_table")).build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            assertTrue(e.getMessage().contains("[bad]"), e.getMessage());
            // the hook runner rolled the transaction back, schema selection included, so the restore
            // had nothing left to undo and the connection is neither aborted nor on the wrong schema
            assertEquals("sa", connection.getSchema());
            assertFalse(connection.getAutoCommit());
            assertEquals(0, scalar(connection, "select count(*) from detail"));
        }
    }

    @Test
    void manualCommitForeignKeyToAnExcludedTableFailsWithTheOriginalSchemaAndAutocommitOff() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .schema("sb").excludeTables("customers").build();

            assertThrows(IllegalArgumentException.class, filler::fill);

            assertEquals("sa", connection.getSchema());
            assertFalse(connection.getAutoCommit());
            assertEquals(0, scalar(connection, "select count(*) from detail"), "the connection is still usable");
        }
    }

    @Test
    void manualCommitFillThatAbortsTheTransactionSurfacesTheFillErrorAndRollbackBringsTheSchemaBack() throws SQLException {
        try (Connection connection = manualCommitConnectionOnSa()) {
            SqlScriptRunner.run(connection, SqlScript.inline("unfillable",
                    "create table sb.unfillable (id int primary key, v int check (v = 1 and v = 2))"));
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .schema("sb").includeTables("unfillable").build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            // the caller sees the fill's own error; the failed restore (the transaction is aborted) is
            // attached to it rather than replacing it
            assertTrue(e.getMessage().contains("check"), e.getMessage());
            assertFalse(connection.getAutoCommit());
            // until the caller rolls back, an aborted transaction accepts nothing
            assertThrows(SQLException.class, () -> scalar(connection, "select 1"));

            connection.rollback();

            // the rollback undid the selection along with everything else in the transaction
            assertEquals("sa", connection.getSchema());
            assertEquals(0, scalar(connection, "select count(*) from detail"));
            assertFalse(connection.getAutoCommit());
        }
    }

    private static String dump(Connection connection) throws SQLException {
        StringBuilder rows = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select id, grp, amount, label from public.detail_seed order by id")) {
            while (rs.next()) {
                rows.append(rs.getInt(1)).append('|').append(rs.getInt(2)).append('|')
                        .append(rs.getInt(3)).append('|').append(rs.getString(4)).append('\n');
            }
        }
        return rows.toString();
    }
}
