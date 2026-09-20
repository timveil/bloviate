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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the before/after SQL hooks of {@link DatabaseFiller} against in-memory H2 (no Docker), on the
 * single-{@link Connection} path and on the {@link javax.sql.DataSource} paths, sequential and with
 * worker threads.
 *
 * <p>The schema has one table that is really filled ({@code detail}) and two that the configuration
 * pins at zero rows ({@code summary}, {@code trace}), so hooks can write to them without the fill
 * adding random rows of its own.
 */
class DatabaseFillerHooksTest extends BaseDatabaseTestCase {

    private static final int ROWS = 40;

    private static final String SCHEMA = """
            create table detail (id int primary key, grp int, amount int);
            create table summary (grp int, total bigint, n bigint);
            create table trace (id int auto_increment primary key, step varchar(60));
            """;

    private static final String ROLLUP = """
            insert into summary
            select grp, sum(amount), count(*) from detail group by grp;
            """;

    private String url;

    @BeforeEach
    void freshDatabase() throws SQLException {
        url = "jdbc:h2:mem:hooks_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", SCHEMA));
        }
    }

    private static DatabaseConfiguration configuration() {
        return new DatabaseConfiguration(16, ROWS, new H2Support(),
                Set.of(new TableConfiguration("summary", 0), new TableConfiguration("trace", 0)), 42L);
    }

    private HikariDataSource dataSource(int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setMaximumPoolSize(poolSize);
        // a hook holding a connection across the fill would starve the workers; fail fast, not in 30s
        config.setConnectionTimeout(5_000);
        return new HikariDataSource(config);
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<String> trace(Connection connection) throws SQLException {
        List<String> steps = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select step from trace order by id")) {
            while (rs.next()) {
                steps.add(rs.getString(1));
            }
        }
        return steps;
    }

    /** Asserts the rollup the after hook computed agrees with the rows the fill generated. */
    private static void assertRollupAgrees(Connection connection) throws SQLException {
        assertEquals(ROWS, scalar(connection, "select count(*) from detail"));
        assertEquals(scalar(connection, "select sum(amount) from detail"),
                scalar(connection, "select sum(total) from summary"));
        assertEquals(ROWS, scalar(connection, "select sum(n) from summary"));
        assertEquals(scalar(connection, "select count(distinct grp) from detail"),
                scalar(connection, "select count(*) from summary"));
    }

    private static DatabaseFiller.Builder tracing(DatabaseFiller.Builder builder) {
        return builder
                .before(SqlScript.inline("b1", "insert into trace(step) select 'b1 saw ' || count(*) from detail"))
                .before(SqlScript.inline("b2", "insert into trace(step) select 'b2 saw ' || count(*) from detail"))
                .after(SqlScript.inline("a1", "insert into trace(step) select 'a1 saw ' || count(*) from detail"))
                .after(SqlScript.inline("a2", "insert into trace(step) select 'a2 saw ' || count(*) from detail"));
    }

    private static final List<String> EXPECTED_TRACE =
            List.of("b1 saw 0", "b2 saw 0", "a1 saw " + ROWS, "a2 saw " + ROWS);

    @Test
    void afterHookRollupAgreesWithFilledRowsOnAConnection() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration())
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertRollupAgrees(connection);
        }
    }

    @Test
    void afterHookCanCreateADerivedTable() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration())
                    .after(SqlScript.inline("derive", "create table by_grp as select grp, sum(amount) total from detail group by grp"))
                    .build().fill();

            // created after the schema was read, so the fill did not touch it; only the hook wrote it
            assertEquals(scalar(connection, "select count(distinct grp) from detail"), scalar(connection, "select count(*) from by_grp"));
        }
    }

    @Test
    void beforeHookRunsAheadOfTheSchemaReadSoItCanCreateTablesToFill() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration())
                    .before(SqlScript.inline("ddl", "create table late (id int primary key, label varchar(20))"))
                    .build().fill();

            assertEquals(ROWS, scalar(connection, "select count(*) from late"));
        }
    }

    @Test
    void hooksRunInOrderAroundTheFillOnAConnection() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            tracing(new DatabaseFiller.Builder(connection, configuration())).build().fill();

            assertEquals(EXPECTED_TRACE, trace(connection));
        }
    }

    @Test
    void hooksWorkOnADataSourceWithoutThreads() throws SQLException {
        try (HikariDataSource dataSource = dataSource(2);
             Connection connection = DriverManager.getConnection(url)) {
            tracing(new DatabaseFiller.Builder(dataSource, configuration()))
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertEquals(EXPECTED_TRACE, trace(connection));
            assertRollupAgrees(connection);
        }
    }

    @Test
    void hooksWorkOnADataSourceWithWorkerThreads() throws SQLException {
        // a pool no larger than the worker count: hooks must borrow and return, not pin, a connection
        try (HikariDataSource dataSource = dataSource(3);
             Connection connection = DriverManager.getConnection(url)) {
            tracing(new DatabaseFiller.Builder(dataSource, configuration()).threads(3))
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertEquals(EXPECTED_TRACE, trace(connection));
            assertRollupAgrees(connection);
        }
    }

    @Test
    void failingBeforeHookFailsTheFillAndFillsNothing() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .before(SqlScript.inline("good", "insert into trace(step) values ('ran')"))
                    .before(SqlScript.inline("bad", "select 1;\nselect * from no_such_table"))
                    .before(SqlScript.inline("never", "insert into trace(step) values ('skipped')"))
                    .build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            assertTrue(e.getMessage().contains("[bad]"), e.getMessage());
            assertTrue(e.getMessage().contains("statement 2"), e.getMessage());
            assertNotNull(e.getCause());
            assertEquals(0, scalar(connection, "select count(*) from detail"), "no table may be filled");
            assertEquals(List.of("ran"), trace(connection), "hooks stop at the first failure");
        }
    }

    @Test
    void failingBeforeHookFillsNothingOnTheParallelPathEither() throws SQLException {
        try (HikariDataSource dataSource = dataSource(3);
             Connection connection = DriverManager.getConnection(url)) {
            DatabaseFiller filler = new DatabaseFiller.Builder(dataSource, configuration()).threads(3)
                    .before(SqlScript.inline("bad", "select * from no_such_table"))
                    .build();

            assertThrows(SQLException.class, filler::fill);

            assertEquals(0, scalar(connection, "select count(*) from detail"));
        }
    }

    @Test
    void failingAfterHookIsNotSwallowedAndEarlierTablesStayFilled() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .after(SqlScript.inline("bad-rollup", "insert into summary select nope from detail"))
                    .build();

            SQLException e = assertThrows(SQLException.class, filler::fill);

            assertTrue(e.getMessage().contains("[bad-rollup]"), e.getMessage());
            assertTrue(e.getMessage().contains("statement 1"), e.getMessage());
            assertEquals(ROWS, scalar(connection, "select count(*) from detail"),
                    "there is no cross-table rollback: the filled tables stay filled");
        }
    }

    @Test
    void afterHooksDoNotRunWhenTheFillItselfFails() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    // a table no generated row can satisfy makes the fill itself fail
                    .before(SqlScript.inline("unfillable", "create table bad (id int primary key, v int check (v = 1 and v = 2))"))
                    .after(SqlScript.inline("after", "insert into trace(step) values ('after ran')"))
                    .build();

            assertThrows(SQLException.class, filler::fill);

            assertEquals(List.of(), trace(connection), "an after hook must not run over a failed fill");
        }
    }

    @Test
    void truncatingInABeforeHookMakesARerunPossible() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            for (int run = 0; run < 2; run++) {
                new DatabaseFiller.Builder(connection, configuration())
                        .before(SqlScript.inline("reset", "truncate table summary; truncate table detail"))
                        .after(SqlScript.inline("rollup", ROLLUP))
                        .build().fill();
                assertRollupAgrees(connection);
            }
        }
    }

    @Test
    void autocommitConnectionIsLeftInAutocommit() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            assertTrue(connection.getAutoCommit());
            tracing(new DatabaseFiller.Builder(connection, configuration())).build().fill();
            assertTrue(connection.getAutoCommit());

            // an engine-managed commit strategy toggles autocommit for the fill and restores it; the
            // hooks around it must neither disturb that nor leave the connection changed
            DatabaseConfiguration perTable = new DatabaseConfiguration(16, ROWS, new H2Support(),
                    Set.of(new TableConfiguration("summary", 0), new TableConfiguration("trace", 0)),
                    42L, CommitStrategy.perTable());
            new DatabaseFiller.Builder(connection, perTable)
                    .before(SqlScript.inline("reset", "delete from detail"))
                    .after(SqlScript.inline("rollup", "delete from summary; " + ROLLUP))
                    .build().fill();
            assertTrue(connection.getAutoCommit());
            assertRollupAgrees(connection);
        }
    }

    @Test
    void manualCommitConnectionStaysManualAndHooksCommit() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);

            new DatabaseFiller.Builder(connection, configuration())
                    .after(SqlScript.inline("rollup", ROLLUP))
                    .build().fill();

            assertFalse(connection.getAutoCommit(), "hooks must not switch a manual-commit connection to autocommit");
            // the connection's default commit strategy leaves the fill to the caller; the after hook's
            // commit made everything visible, fill rows included
            assertRollupAgrees(observer);
        }
    }

    @Test
    void manualCommitBeforeHookFailureRollsBackItsOwnWork() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration())
                    .before(SqlScript.inline("atomic", "insert into trace(step) values ('x'); select * from no_such_table"))
                    .build();

            assertThrows(SQLException.class, filler::fill);

            assertFalse(connection.getAutoCommit());
            assertEquals(0, scalar(connection, "select count(*) from trace"));
        }
    }

    @Test
    void hookRegistrationRejectsNullAndIsDefensivelyCopied() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            DatabaseFiller.Builder builder = new DatabaseFiller.Builder(connection, configuration());
            assertThrows(NullPointerException.class, () -> builder.before(null));
            assertThrows(NullPointerException.class, () -> builder.after(null));

            DatabaseFiller filler = builder.after(SqlScript.inline("one", "select 1")).build();
            // a hook added after build() must not leak into the built filler (it would fail the fill)
            builder.after(SqlScript.inline("late", "select * from no_such_table"));

            filler.fill();
            assertEquals(ROWS, scalar(connection, "select count(*) from detail"));
        }
    }
}
