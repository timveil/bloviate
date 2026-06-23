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
import io.bloviate.gen.DataGenerator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Random;

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
    DataGenerator<?> getDataGenerator(Column column, Random random);

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
     * <p><strong>CockroachDB caveat:</strong> CockroachDB is typically reached through the
     * PostgreSQL JDBC driver, which reports its product name as {@code "PostgreSQL"}. Such
     * connections therefore resolve to {@link PostgresSupport}, not
     * {@link CockroachDBSupport}. To use the CockroachDB-specific generators (jsonb, inet,
     * arrays, bit strings, ...), pass {@code new CockroachDBSupport()} explicitly rather
     * than relying on auto-selection.
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
