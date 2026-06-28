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

package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.db.ColumnConstraint;
import io.bloviate.gen.DataGenerator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Database-specific support interface for data generation and SQL handling.
 * 
 * <p>This interface defines the single contract for database-specific implementations:
 * mapping a {@link Column} to an appropriate {@link DataGenerator}. The cross-database
 * defaults and the per-{@link java.sql.JDBCType} registry live in
 * {@link AbstractDatabaseSupport}; database-specific subclasses customize generation by
 * registering or replacing factories rather than implementing this interface directly.
 *
 * <p>Common implementations include {@link PostgresSupport}, {@link MySQLSupport},
 * {@link CockroachDBSupport}, and {@link DefaultSupport} for generic databases.
 *
 * @author Tim Veil
 * @see DataGenerator
 * @see Column
 * @see AbstractDatabaseSupport
 * @see GeneratorFactory
 */
public interface DatabaseSupport {

    /**
     * Creates an appropriate data generator for the given column based on its
     * {@link java.sql.JDBCType} (and, where relevant, its database-specific type name).
     *
     * @param column the column metadata including type and constraints
     * @param random the random number generator for seeded data generation
     * @return a data generator appropriate for the column type
     * @throws UnsupportedOperationException if the column type is not supported
     */
    DataGenerator<?> getDataGenerator(Column column, RandomGenerator random);

    /**
     * Returns the JDBC-URL parameter name that enables this driver's <em>batch rewrite</em>
     * optimization &mdash; collapsing a JDBC batch into a single multi-row {@code INSERT} &mdash;
     * or {@code null} if the database has no such parameter.
     *
     * <p>Bloviate fills through a {@link Connection}/{@link javax.sql.DataSource} it does not own,
     * so it cannot add this parameter to the URL after the fact. Instead, the fill engine reads it
     * to warn when the parameter is absent (it is often the single biggest fill speedup), and
     * {@link io.bloviate.util.JdbcUrls} uses it to help callers build a correctly-parameterized URL.
     *
     * <p>Known values: PostgreSQL {@code reWriteBatchedInserts}, MySQL
     * {@code rewriteBatchedStatements}. CockroachDB ignores batch rewrite, so it returns
     * {@code null}; the generic {@link DefaultSupport} also returns {@code null}.
     *
     * @return the batch-rewrite URL parameter name, or {@code null} if not applicable
     * @since 2.10.0
     */
    default String batchRewriteUrlParameter() {
        return null;
    }

    /**
     * Reads the value constraints (CHECK constraints and enum/domain allowed values) for a table's
     * columns, so the fill engine can generate values that satisfy them (issue #479). The default
     * returns an empty map (no constraint awareness); database-specific implementations override it
     * with vendor catalog queries.
     *
     * <p>Constraint reading is best-effort and must never fail a fill: an implementation that cannot
     * read the catalog should log and return what it has.
     *
     * @param connection an open connection to query the catalog with
     * @param schema     the table's schema (may be null for the default schema)
     * @param table      the table name
     * @return constraints keyed by lower-cased column name; empty when none are found or supported
     * @since 2.14.0
     */
    default Map<String, ColumnConstraint> readConstraints(Connection connection, String schema, String table) {
        return Map.of();
    }

    /**
     * Selects a {@link DatabaseSupport} for the given JDBC product name (as reported by
     * {@link java.sql.DatabaseMetaData#getDatabaseProductName()}), so callers don't have
     * to hardcode a specific implementation.
     *
     * <p>Matching is case-insensitive and substring-based: names containing
     * {@code "cockroach"} map to {@link CockroachDBSupport}, {@code "mysql"} to
     * {@link MySQLSupport}, and {@code "postgres"} to {@link PostgresSupport}. Anything
     * else (including {@code null}) falls back to {@link DefaultSupport}.
     *
     * <p><strong>CockroachDB note:</strong> CockroachDB is typically reached through the
     * PostgreSQL JDBC driver, which reports its product name as {@code "PostgreSQL"}, so such
     * connections resolve to {@link PostgresSupport}. Because {@link CockroachDBSupport}
     * extends {@link PostgresSupport} and adds no extra behavior, this resolves the same
     * type handling (uuid, jsonb, inet, intervals, arrays, bit strings, ...); passing
     * {@code new CockroachDBSupport()} explicitly is equivalent.
     *
     * @param productName the database product name, may be null
     * @return the matching support, or {@link DefaultSupport} if none matches
     */
    static DatabaseSupport forProduct(String productName) {
        if (productName != null) {
            String name = productName.toLowerCase(Locale.ROOT);
            if (name.contains("cockroach")) {
                return new CockroachDBSupport();
            }
            if (name.contains("mysql")) {
                return new MySQLSupport();
            }
            if (name.contains("postgres")) {
                return new PostgresSupport();
            }
        }
        return new DefaultSupport();
    }

    /**
     * Selects a {@link DatabaseSupport} by reading the product name from the connection's
     * metadata. See {@link #forProduct(String)} for the matching rules and the CockroachDB
     * caveat.
     *
     * @param connection an open database connection
     * @return the matching support, or {@link DefaultSupport} if none matches
     * @throws SQLException if the connection metadata cannot be read
     */
    static DatabaseSupport forConnection(Connection connection) throws SQLException {
        return forProduct(connection.getMetaData().getDatabaseProductName());
    }

}
