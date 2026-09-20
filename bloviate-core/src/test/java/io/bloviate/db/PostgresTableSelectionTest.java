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
