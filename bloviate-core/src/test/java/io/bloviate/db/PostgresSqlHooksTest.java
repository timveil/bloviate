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

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the before/after SQL hooks against a real PostgreSQL: a dollar-quoted PL/pgSQL function whose
 * body holds semicolons, comments and tagged quotes (created by a before hook and called by an after
 * hook), the {@code TRUNCATE} re-run workaround, and both fill paths (single connection, and pooled
 * with worker threads).
 */
class PostgresSqlHooksTest extends BaseDatabaseTestCase {

    private static final int ROWS = 60;

    private static DatabaseConfiguration configuration() {
        return new DatabaseConfiguration(32, ROWS, new PostgresSupport(),
                Set.of(new TableConfiguration("summary", 0)), 42L);
    }

    private static SqlScript before() {
        return SqlScript.resource("hooks_before.postgres.sql", PostgresSqlHooksTest.class.getClassLoader())
                .withTokens(Map.of("target", "summary"));
    }

    private static SqlScript after() {
        return SqlScript.inline("rollup", "SELECT rollup_detail();");
    }

    private static void assertRollupAgrees(Connection connection) throws SQLException {
        assertRowCount(connection, "detail", ROWS);
        assertCount(connection, "select count(*) from (select grp from detail group by grp) g", scalar(connection, "select count(*) from summary"));
        assertCount(connection, "select count(*) from summary s join "
                + "(select grp, sum(amount) total, count(*) n from detail group by grp) d "
                + "on s.grp = d.grp and s.total = d.total and s.n = d.n", scalar(connection, "select count(*) from summary"));
        assertCount(connection, "select sum(n) from summary", ROWS);
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
             var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void dollarQuotedFunctionRollupAgreesOnAConnectionAndThroughAPool() throws SQLException {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>(TestImages.POSTGRES)
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_hooks.postgres.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database)) {

                // single caller-managed connection
                try (Connection connection = dataSource.getConnection()) {
                    new DatabaseFiller.Builder(connection, configuration())
                            .before(before()).after(after()).build().fill();
                    assertTrue(connection.getAutoCommit(), "the connection's autocommit must be unchanged");
                    assertRollupAgrees(connection);
                }

                // the same database again: only the before hook's TRUNCATE makes this re-run possible,
                // and this time through the pool with worker threads
                new DatabaseFiller.Builder(dataSource, configuration()).threads(3)
                        .before(before()).after(after()).build().fill();

                try (Connection connection = dataSource.getConnection()) {
                    assertRollupAgrees(connection);
                }
            }
        }
    }

    @Test
    void reRunWithoutTheTruncateHookCollidesOnThePrimaryKey() throws SQLException {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>(TestImages.POSTGRES)
                .withDatabaseName("bloviate")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_hooks.postgres.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database);
                 Connection connection = dataSource.getConnection()) {
                new DatabaseFiller.Builder(connection, configuration()).build().fill();

                // documents why the before-TRUNCATE workaround exists: a fill is not idempotent
                DatabaseFiller again = new DatabaseFiller.Builder(connection, configuration()).build();
                assertThrows(SQLException.class, again::fill);
            }
        }
    }

    @Test
    void failingStatementInAHookReportsScriptAndStatementOnPostgres() throws SQLException {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>(TestImages.POSTGRES)
                .withDatabaseName("bloviate")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_hooks.postgres.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database);
                 Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                        .before(SqlScript.inline("guard", "SELECT 1; SELECT 1 / 0; SELECT 2"))
                        .build();

                SQLException e = assertThrows(SQLException.class, filler::fill);

                assertTrue(e.getMessage().contains("[guard]"), e.getMessage());
                assertTrue(e.getMessage().contains("statement 2"), e.getMessage());
                assertEquals("22012", e.getSQLState(), "the driver's SQL state is preserved");
                // the failed script rolled back, so the connection is usable and nothing was filled
                connection.rollback();
                assertRowCount(connection, "detail", 0);
            }
        }
    }
}
