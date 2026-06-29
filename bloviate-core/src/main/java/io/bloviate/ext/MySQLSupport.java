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

import io.bloviate.db.Database;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.SimpleStringGenerator;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * MySQL-specific {@link DatabaseSupport}.
 *
 * <p>MySQL's JDBC driver maps most types onto standard JDBC types that the cross-database
 * defaults already handle. The notable exception is {@code JSON}, which the driver reports
 * as {@link JDBCType#LONGVARCHAR} (type name {@code JSON}); a random string would fail the
 * server's JSON validation, so this support routes {@code JSON} columns to a JSON generator
 * while leaving ordinary text columns on the default string generator.
 *
 * <p>Some MySQL types remain unsupported because they need value-aware or binary generation
 * that standard JDBC metadata doesn't expose: {@code ENUM}/{@code SET} (must match the
 * declared member list), {@code GEOMETRY} (well-known binary), and {@code YEAR}.
 *
 * @since 1.0.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class MySQLSupport extends AbstractDatabaseSupport {

    /** Creates the MySQL support with its default configuration. */
    public MySQLSupport() {
    }

    /**
     * Returns {@code rewriteBatchedStatements}, the MySQL Connector/J parameter that rewrites a batch
     * of single-row {@code INSERT}s into one multi-row statement. Enabling it (e.g.
     * {@code jdbc:mysql://host/db?rewriteBatchedStatements=true}) collapses many round trips into one
     * and is often the single biggest fill speedup; Bloviate logs a one-time recommendation when a
     * fill connection's URL does not set it.
     *
     * @return the {@code rewriteBatchedStatements} parameter name
     * @since 2.10.0
     */
    @Override
    public String batchRewriteUrlParameter() {
        return "rewriteBatchedStatements";
    }

    /**
     * MySQL supports unordered bulk loading by disabling the session's foreign-key and unique checks.
     *
     * @return {@code true}
     * @since 2.17.0
     */
    @Override
    public boolean supportsBulkLoad() {
        return true;
    }

    /**
     * Disables {@code FOREIGN_KEY_CHECKS} and {@code UNIQUE_CHECKS} for the session. These are ordinary
     * session variables that need no special privilege. MySQL does not re-validate existing rows when
     * the checks are turned back on, which is acceptable because bulk-loaded data is referentially
     * consistent by construction.
     *
     * @param connection an open connection whose session variables are changed
     * @param database   the database metadata (unused by this mechanism)
     * @return a handle recording the disabled checks
     * @throws SQLException if a statement fails
     */
    @Override
    public BulkLoadHandle disableConstraints(Connection connection, Database database) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("SET UNIQUE_CHECKS=0");
        }
        return BulkLoadHandle.of("FOREIGN_KEY_CHECKS=0, UNIQUE_CHECKS=0");
    }

    /**
     * Restores {@code UNIQUE_CHECKS} and {@code FOREIGN_KEY_CHECKS} to {@code 1} for the session before
     * the connection is returned to the pool.
     *
     * @param connection the same connection passed to {@link #disableConstraints}
     * @param database   the database metadata (unused by this mechanism)
     * @param handle     the handle returned by {@link #disableConstraints}
     * @throws SQLException if a statement fails
     */
    @Override
    public void enableConstraints(Connection connection, Database database, BulkLoadHandle handle) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET UNIQUE_CHECKS=1");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // MySQL JSON columns report as LONGVARCHAR with type name "JSON". Generate valid JSON
        // for those; everything else on this JDBC type stays an ordinary string.
        registry.put(JDBCType.LONGVARCHAR, (column, random) -> {
            if ("json".equalsIgnoreCase(column.typeName())) {
                return new JsonbGenerator.Builder(random).build();
            }
            Integer maxSize = column.maxSize();
            int size = (maxSize == null || maxSize <= 0) ? 2000 : maxSize;
            return new SimpleStringGenerator.Builder(random).size(size).build();
        });
    }
}
