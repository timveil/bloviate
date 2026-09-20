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
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One PostgreSQL container that hosts a fixture with <em>several schemas</em>, one per scenario
 * (issue #614), so a class can exercise many independent schemas for the cost of a single container
 * start-up.
 *
 * <p>The engine introspects only the connection's current schema, so every fill runs on a pool whose
 * schema is pinned to the scenario ({@link HikariConfig#setSchema}). That includes the parallel path,
 * which borrows further connections from the same pool. Scenario schemas are independent: nothing here
 * changes another schema's tables, and {@link #reset(String)} empties one so a schema can be filled
 * more than once.
 *
 * <p>Foreign keys are real and enforced (no {@code session_replication_role} change, no
 * {@code NOT VALID}); {@link #assertForeignKeysEnforced(String, int)} checks that, so an assertion
 * that "every child row has a parent" cannot pass merely because enforcement was off.
 */
final class PostgresSchemaFixture implements AutoCloseable {

    private final PostgreSQLContainer<?> container;

    PostgresSchemaFixture(String initScript) {
        container = new PostgreSQLContainer<>(TestImages.POSTGRES)
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedInserts", "true")
                // same rationale as BasePostgresTest: let the server infer string-bound parameter types
                .withUrlParam("stringtype", "unspecified")
                .withInitScript(initScript);
        container.start();
    }

    /** A pool whose connections all have {@code schema} as their current schema. Caller closes it. */
    HikariDataSource dataSource(String schema) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setDriverClassName(container.getDriverClassName());
        config.setSchema(schema);
        return new HikariDataSource(config);
    }

    /** Fills the schema on a single caller-managed connection: the default, sequential path. */
    void fillSequential(String schema, DatabaseConfiguration configuration) throws SQLException {
        try (HikariDataSource dataSource = dataSource(schema);
             Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration).build().fill();
        }
    }

    /** Fills the schema from a pool with {@code threads} workers: the parallel, level-by-level path. */
    void fillParallel(String schema, DatabaseConfiguration configuration, int threads) throws SQLException {
        try (HikariDataSource dataSource = dataSource(schema)) {
            new DatabaseFiller.Builder(dataSource, configuration).threads(threads).build().fill();
        }
    }

    /** Empties every table in the schema, so the same schema can be filled again. */
    void reset(String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = container.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select quote_ident(schemaname) || '.' || quote_ident(tablename) from pg_tables where schemaname = '"
                             + schema + "'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        if (!tables.isEmpty()) {
            update("truncate table " + String.join(", ", tables) + " restart identity cascade");
        }
    }

    void update(String sql) throws SQLException {
        try (Connection connection = container.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    long count(String qualifiedTable) throws SQLException {
        return queryLong("select count(*) from " + qualifiedTable);
    }

    long queryLong(String sql) throws SQLException {
        try (Connection connection = container.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    String queryString(String sql) throws SQLException {
        try (Connection connection = container.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    /**
     * Asserts that the schema has exactly {@code expected} foreign-key constraints and that all of
     * them are validated (not {@code NOT VALID}) and enforced (the session replication role is the
     * default), so "the fill succeeded" genuinely implies referential integrity.
     */
    void assertForeignKeysEnforced(String schema, int expected) throws SQLException {
        assertEquals(expected, queryLong("select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                + "where c.contype = 'f' and c.conparentid = 0 and c.convalidated and n.nspname = '" + schema + "'"),
                "validated foreign keys in schema " + schema);
        assertEquals(0, queryLong("select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                + "where c.contype = 'f' and not c.convalidated and n.nspname = '" + schema + "'"),
                "unvalidated foreign keys in schema " + schema);
        assertEquals("origin", queryString("show session_replication_role"), "foreign-key triggers must be enabled");
        assertEquals(0, queryLong("select count(*) from pg_trigger t join pg_class r on r.oid = t.tgrelid "
                + "join pg_namespace n on n.oid = r.relnamespace where t.tgconstraint <> 0 and t.tgenabled <> 'O' "
                + "and n.nspname = '" + schema + "'"), "no constraint trigger may be disabled");
    }

    @Override
    public void close() {
        container.stop();
    }
}
