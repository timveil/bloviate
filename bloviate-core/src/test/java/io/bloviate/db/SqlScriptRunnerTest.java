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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link SqlScriptRunner} and {@link SqlScript} against an in-memory H2 database (no Docker):
 * execution, script sources, failure reporting, transaction semantics and autocommit preservation.
 */
class SqlScriptRunnerTest extends BaseDatabaseTestCase {

    private String url;

    @BeforeEach
    void freshDatabase() throws SQLException {
        // a uniquely named in-memory database per test; kept alive for the connections' lifetime
        url = "jdbc:h2:mem:script_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("create table t (id int primary key, note varchar(100))");
        }
    }

    private long count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var rs = statement.executeQuery("select count(*) from t")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void runsEveryStatementAndReportsTheCount() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            int executed = SqlScriptRunner.run(connection, SqlScript.inline("seed", """
                    -- seed data; with a comment
                    insert into t values (1, 'a;b');
                    insert into t values (2, 'it''s');
                    insert into t values (3, 'c')"""));
            assertEquals(3, executed);
            assertEquals(3, count(connection));
        }
    }

    @Test
    void emptyScriptDoesNothing() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            assertEquals(0, SqlScriptRunner.run(connection, SqlScript.inline("-- nothing;\n")));
        }
    }

    @Test
    void dollarQuotedBodyWithSemicolonsRunsOnH2() throws SQLException {
        // H2 accepts a Java source body in $$...$$; the semicolons inside must not split the statement
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("alias", """
                    create alias add_one as $$
                    int addOne(int a) {
                        int result = a;
                        result += 1;
                        return result;
                    }
                    $$;
                    insert into t select add_one(41), 'x'"""));
            try (Statement statement = connection.createStatement();
                 var rs = statement.executeQuery("select id from t")) {
                assertTrue(rs.next());
                assertEquals(42, rs.getInt(1));
            }
        }
    }

    @Test
    void failingStatementNamesScriptStatementAndFirstLineAndKeepsCause() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScript script = SqlScript.inline("broken", """
                    insert into t values (1, 'ok');
                    insert into no_such_table
                        values (2, 'never');
                    insert into t values (3, 'not reached')""");

            SQLException e = assertThrows(SQLException.class, () -> SqlScriptRunner.run(connection, script));

            assertTrue(e.getMessage().contains("[broken]"), e.getMessage());
            assertTrue(e.getMessage().contains("statement 2"), e.getMessage());
            assertTrue(e.getMessage().contains("line 2"), e.getMessage());
            assertTrue(e.getMessage().contains("insert into no_such_table"), e.getMessage());
            // the driver's own message may echo the whole statement; what the runner adds is one line
            assertFalse(e.getMessage().replace(e.getCause().getMessage(), "").contains("never"),
                    "only the first line of the statement is quoted");
            assertNotNull(e.getCause());
            assertInstanceOf(SQLException.class, e.getCause());
            assertEquals(((SQLException) e.getCause()).getSQLState(), e.getSQLState());

            // autocommit: the statement before the failure stays applied, the one after never ran
            assertEquals(1, count(connection));
        }
    }

    @Test
    void longStatementExcerptIsTruncated() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            String longName = "x".repeat(300);
            SQLException e = assertThrows(SQLException.class,
                    () -> SqlScriptRunner.run(connection, SqlScript.inline("long", "select * from " + longName)));
            assertTrue(e.getMessage().contains("..."), e.getMessage());
            assertTrue(e.getMessage().length() < 600, "excerpt should be truncated: " + e.getMessage().length());
        }
    }

    @Test
    void autocommitConnectionKeepsAutocommitAndCommitsEachStatement() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            assertTrue(connection.getAutoCommit());
            SqlScriptRunner.run(connection, SqlScript.inline("two", "insert into t values (1,'a'); insert into t values (2,'b')"));
            assertTrue(connection.getAutoCommit(), "the runner must not change autocommit");
            assertEquals(2, count(observer), "autocommit statements are committed as they run");
        }
    }

    @Test
    void manualCommitConnectionCommitsAfterTheScriptAndStaysManual() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            SqlScriptRunner.run(connection, SqlScript.inline("two", "insert into t values (1,'a'); insert into t values (2,'b')"));
            assertFalse(connection.getAutoCommit(), "the runner must not change autocommit");
            assertEquals(2, count(observer), "the script is committed once it succeeds");
        }
    }

    @Test
    void manualCommitConnectionRollsBackTheWholeScriptOnFailure() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
             Connection observer = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            SQLException e = assertThrows(SQLException.class, () -> SqlScriptRunner.run(connection, SqlScript.inline("atomic",
                    "insert into t values (1,'a'); insert into t values (1,'duplicate key')")));
            assertTrue(e.getMessage().contains("statement 2"), e.getMessage());
            assertFalse(connection.getAutoCommit(), "a failure must not change autocommit either");
            assertEquals(0, count(connection), "the first statement was rolled back with the failure");
            assertEquals(0, count(observer));
        }
    }

    @Test
    void unresolvedTokenFailsBeforeAnyStatementRuns() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScript script = SqlScript.inline("tokens", "insert into t values (1, 'x');\ninsert into t values (2, '${who}')")
                    .withTokens(Map.of("other", "y"));

            SQLException e = assertThrows(SQLException.class, () -> SqlScriptRunner.run(connection, script));

            assertTrue(e.getMessage().contains("[tokens]"), e.getMessage());
            assertTrue(e.getMessage().contains("${who}"), e.getMessage());
            assertTrue(e.getMessage().contains("line 2"), e.getMessage());
            assertEquals(0, count(connection), "the script is validated in full before the first statement runs");
        }
    }

    @Test
    void tokensAreSubstitutedAndAccumulate() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScript script = SqlScript.inline("tokens", "insert into ${table} values (${id}, '${note}')")
                    .withTokens(Map.of("table", "t", "id", "5"))
                    .withTokens(Map.of("note", "hello; world"));
            assertEquals(Map.of("table", "t", "id", "5", "note", "hello; world"), script.tokens());

            assertEquals(1, SqlScriptRunner.run(connection, script));

            try (Statement statement = connection.createStatement();
                 var rs = statement.executeQuery("select id, note from t")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1));
                assertEquals("hello; world", rs.getString(2));
            }
        }
    }

    @Test
    void delimiterCommandFailsWithAClearMessage() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            SQLException e = assertThrows(SQLException.class, () -> SqlScriptRunner.run(connection,
                    SqlScript.inline("mysql-dump", "DELIMITER //\ncreate procedure p() begin end//\nDELIMITER ;")));
            assertTrue(e.getMessage().contains("[mysql-dump]"), e.getMessage());
            assertTrue(e.getMessage().contains("DELIMITER"), e.getMessage());
            assertInstanceOf(IllegalArgumentException.class, e.getCause());
        }
    }

    @Test
    void readsScriptsFromFilesAndClasspathResources(@TempDir Path dir) throws SQLException, IOException {
        Path file = dir.resolve("seed.sql");
        Files.writeString(file, "insert into t values (1, 'file');\n");
        try (Connection connection = DriverManager.getConnection(url)) {
            assertEquals(file.toString(), SqlScript.file(file).name());
            SqlScriptRunner.run(connection, SqlScript.file(file));
            SqlScriptRunner.run(connection, SqlScript.resource("hook_seed.h2.sql")
                    .withTokens(Map.of("id", "2")));
            assertEquals(2, count(connection));
        }
    }

    @Test
    void unreadableScriptSourcesFailAsSqlExceptions(@TempDir Path dir) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            SQLException missingFile = assertThrows(SQLException.class,
                    () -> SqlScriptRunner.run(connection, SqlScript.file(dir.resolve("absent.sql"))));
            assertTrue(missingFile.getMessage().contains("could not read SQL script"), missingFile.getMessage());
            assertInstanceOf(IOException.class, missingFile.getCause());

            SQLException missingResource = assertThrows(SQLException.class,
                    () -> SqlScriptRunner.run(connection, SqlScript.resource("no/such/script.sql")));
            assertTrue(missingResource.getMessage().contains("no/such/script.sql"), missingResource.getMessage());
        }
    }

    @Test
    void runAllRunsInOrderAndStopsAtTheFirstFailure() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            List<SqlScript> scripts = List.of(
                    SqlScript.inline("one", "insert into t values (1, 'first')"),
                    SqlScript.inline("two", "insert into t values (1, 'duplicate')"),
                    SqlScript.inline("three", "insert into t values (3, 'never')"));

            SQLException e = assertThrows(SQLException.class, () -> SqlScriptRunner.runAll(connection, scripts));

            assertTrue(e.getMessage().contains("[two]"), e.getMessage());
            assertEquals(1, count(connection));
        }
    }

    @Test
    void testHelperRunsScriptsThroughTheSharedRunner() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            runScript(connection, "hook_seed.h2.sql", Map.of("id", "9"));
            assertEquals(1, count(connection));
            assertThrows(SQLException.class, () -> runScript(connection, "hook_seed.h2.sql"),
                    "an unresolved token in a fixture must fail rather than reach the database");
        }
    }
}
