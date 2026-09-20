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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * Runs {@link SqlScript}s on a JDBC {@link Connection}: the engine behind
 * {@link DatabaseFiller.Builder#before(SqlScript)} and {@link DatabaseFiller.Builder#after(SqlScript)},
 * also usable on its own (for example to load a schema before a fill).
 *
 * <h2>Syntax</h2>
 * A script is split into statements at {@code ;}. A {@code ;} inside a single-quoted string, a
 * double-quoted or backtick-quoted identifier, a {@code --} or slash-star comment, or a PostgreSQL
 * dollar-quoted body ({@code $$...$$}, {@code $tag$...$tag$}, so {@code CREATE FUNCTION} bodies work)
 * does not end a statement. Block comments nest, as in PostgreSQL. Empty statements are skipped and a
 * final statement needs no {@code ;}. The MySQL client's {@code DELIMITER} command is not supported
 * and fails the script with a clear message instead of mis-splitting it, and backslash escapes are
 * honoured only in PostgreSQL {@code E'...'} strings.
 *
 * <h2>Tokens</h2>
 * Every {@code ${name}} outside a comment is replaced with the value of {@link SqlScript#tokens()}.
 * A token with no value fails the script, naming the token and its line, before any statement runs.
 * A script that must contain a literal {@code ${name}} can map that name to itself.
 *
 * <h2>Transactions</h2>
 * A script runs on the connection as it is; the runner never changes its autocommit setting.
 * <ul>
 *   <li><b>Autocommit on:</b> each statement commits by itself. A failure leaves the statements
 *       before it applied.</li>
 *   <li><b>Autocommit off:</b> the script commits once, after its last statement succeeds, and rolls
 *       back on failure, so it is all-or-nothing. The transaction is the connection's open one, so it
 *       also commits (or discards) anything the caller had already run on the connection and not yet
 *       committed.</li>
 * </ul>
 * DDL that a database commits implicitly (MySQL, for one) commits regardless of the mode.
 *
 * <h2>Failures</h2>
 * Any failure &mdash; an unreadable script, a syntax problem, an unresolved token, a failing
 * statement, a failing commit &mdash; is thrown as a {@link SQLException} whose message names the
 * script and, for a failing statement, its number, line and first line of text. The original
 * exception is the cause and its SQL state and vendor code are preserved. Scripts are split and
 * token-checked in full before the first statement runs.
 *
 * @since 3.3.0
 * @see SqlScript
 */
public final class SqlScriptRunner {

    private static final Logger logger = LoggerFactory.getLogger(SqlScriptRunner.class);

    /** Longest statement excerpt included in an error message. */
    private static final int EXCERPT_LENGTH = 80;

    private SqlScriptRunner() {
    }

    /**
     * Runs the scripts in order on {@code connection}, stopping at the first failure. Each script
     * follows the transaction rules in the class documentation independently.
     *
     * @param connection the connection to run on
     * @param scripts    the scripts, in the order to run them
     * @throws SQLException if any script cannot be read or split, or any statement fails
     */
    public static void runAll(Connection connection, List<SqlScript> scripts) throws SQLException {
        for (SqlScript script : scripts) {
            run(connection, script);
        }
    }

    /**
     * Runs one script on {@code connection}; see the class documentation for the syntax, token and
     * transaction rules.
     *
     * @param connection the connection to run on
     * @param script     the script
     * @return the number of statements executed
     * @throws SQLException if the script cannot be read or split, or any statement fails
     */
    public static int run(Connection connection, SqlScript script) throws SQLException {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(script, "script must not be null");

        List<SqlScriptSplitter.Statement> statements = parse(script);
        if (statements.isEmpty()) {
            logger.debug("SQL script [{}] has no statements", script.name());
            return 0;
        }

        boolean manualCommit = !connection.getAutoCommit();
        SqlScriptSplitter.Statement running = null;
        try (Statement statement = connection.createStatement()) {
            for (SqlScriptSplitter.Statement next : statements) {
                running = next;
                logger.debug("SQL script [{}] statement {}: {}", script.name(), next.number(), excerpt(next.sql()));
                statement.execute(next.sql());
            }
            running = null;
            if (manualCommit) {
                connection.commit();
            }
        } catch (SQLException e) {
            if (manualCommit) {
                rollbackQuietly(connection, e);
            }
            throw failure(script, running, e);
        }

        logger.debug("ran SQL script [{}]: {} statement(s)", script.name(), statements.size());
        return statements.size();
    }

    /** Reads, token-substitutes and splits {@code script}, turning every problem into a SQLException. */
    private static List<SqlScriptSplitter.Statement> parse(SqlScript script) throws SQLException {
        String text;
        try {
            text = script.read();
        } catch (IOException e) {
            throw new SQLException("could not read SQL script [" + script.name() + "]: " + e.getMessage(), e);
        }
        try {
            return SqlScriptSplitter.split(text, script.tokens()::get);
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid SQL script [" + script.name() + "]: " + e.getMessage(), e);
        }
    }

    private static SQLException failure(SqlScript script, SqlScriptSplitter.Statement statement, SQLException cause) {
        String where = statement == null
                ? "committing"
                : "statement " + statement.number() + " (line " + statement.line() + ") [" + excerpt(statement.sql()) + "]";
        return new SQLException("SQL script [" + script.name() + "] failed at " + where + ": " + cause.getMessage(),
                cause.getSQLState(), cause.getErrorCode(), cause);
    }

    /** Rolls back after a failure, never letting a rollback problem replace the original cause. */
    private static void rollbackQuietly(Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    /** The first line of the statement, shortened for a log line or error message. */
    private static String excerpt(String sql) {
        int newline = sql.indexOf('\n');
        String first = newline < 0 ? sql : sql.substring(0, newline).stripTrailing();
        return first.length() > EXCERPT_LENGTH ? first.substring(0, EXCERPT_LENGTH) + "..." : first;
    }
}
