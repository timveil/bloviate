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
import io.bloviate.db.Database;
import io.bloviate.gen.BitStringGenerator;
import io.bloviate.gen.BooleanGenerator;
import io.bloviate.gen.CidrGenerator;
import io.bloviate.gen.InetGenerator;
import io.bloviate.gen.IntegerArrayGenerator;
import io.bloviate.gen.IntervalGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.MacAddressGenerator;
import io.bloviate.gen.StringArrayGenerator;
import io.bloviate.gen.UUIDGenerator;
import io.bloviate.gen.XmlGenerator;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * PostgreSQL-specific {@link DatabaseSupport}.
 *
 * <p>On top of the cross-database defaults, this registers generators for the PostgreSQL
 * types the JDBC driver surfaces as {@link JDBCType#OTHER}, {@link JDBCType#ARRAY},
 * {@link JDBCType#SQLXML}, and {@link JDBCType#BIT}, dispatching on the driver-reported
 * type name:
 *
 * <ul>
 *   <li>{@code uuid}, {@code json}, {@code jsonb}, {@code inet}, {@code cidr},
 *       {@code macaddr}, {@code macaddr8}, {@code interval}, {@code varbit} (all reported
 *       as {@code OTHER})</li>
 *   <li>{@code _text}, {@code _int2}, {@code _int4}, {@code _int8} arrays (reported as
 *       {@code ARRAY})</li>
 *   <li>{@code xml} (reported as {@code SQLXML})</li>
 *   <li>{@code bit}/{@code bit(n)} and {@code boolean} (both reported as {@code BIT},
 *       distinguished by type name)</li>
 * </ul>
 *
 * <p>Because CockroachDB speaks the PostgreSQL wire protocol through the same JDBC driver,
 * {@link CockroachDBSupport} extends this class and inherits all of the above.
 *
 * <p><strong>Connection requirement:</strong> these extension types are generated as their
 * text representations and bound as strings. PostgreSQL will not implicitly cast
 * {@code character varying} to {@code uuid}, {@code jsonb}, {@code inet}, {@code bit}, etc.,
 * so the JDBC connection must be opened with {@code stringtype=unspecified} (e.g.
 * {@code jdbc:postgresql://host/db?stringtype=unspecified}) for the server to infer each
 * column's type. Standard column types do not require this.
 *
 * @since 1.0.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class PostgresSupport extends AbstractDatabaseSupport {

    /** Creates a PostgreSQL support instance. */
    public PostgresSupport() {
    }

    /**
     * Returns {@code reWriteBatchedInserts}, the PostgreSQL JDBC driver parameter that rewrites a
     * batch of single-row {@code INSERT}s into one multi-row statement. Enabling it (e.g.
     * {@code jdbc:postgresql://host/db?reWriteBatchedInserts=true}) drastically cuts the number of
     * round trips and is often the single biggest fill speedup; Bloviate logs a one-time
     * recommendation when a fill connection's URL does not set it.
     *
     * @return the {@code reWriteBatchedInserts} parameter name
     * @since 2.10.0
     */
    @Override
    public String batchRewriteUrlParameter() {
        return "reWriteBatchedInserts";
    }

    @Override
    public java.util.Map<String, io.bloviate.db.ColumnConstraint> readConstraints(java.sql.Connection connection, String schema, String table) {
        return PostgresConstraints.read(connection, schema, table);
    }

    /**
     * PostgreSQL supports unordered bulk loading by suppressing foreign-key trigger firing for the
     * session via {@code SET session_replication_role = replica}.
     *
     * @return {@code true}
     * @since 2.17.0
     */
    @Override
    public boolean supportsBulkLoad() {
        return true;
    }

    /**
     * Sets {@code session_replication_role = replica} on the connection, which stops user and
     * foreign-key triggers (including referential-integrity checks) from firing for the session. This
     * is the cheapest bulk-load mechanism — no catalog churn and nothing to rebuild — but it requires a
     * superuser (or {@code rds_superuser}) role. A privilege failure is reported as a
     * {@link BulkLoadUnsupportedException} so the engine can fall back to the ordered path.
     *
     * @param connection an open connection whose session role is changed
     * @param database   the database metadata (unused by this mechanism)
     * @return a handle recording that {@code session_replication_role} was changed
     * @throws BulkLoadUnsupportedException if the role lacks privilege to change the setting
     * @throws SQLException                 if the statement otherwise fails
     */
    @Override
    public BulkLoadHandle disableConstraints(Connection connection, Database database) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET session_replication_role = replica");
        } catch (SQLException e) {
            throw new BulkLoadUnsupportedException(
                    "could not set session_replication_role=replica (a superuser/rds_superuser role is required): "
                            + e.getMessage(), e);
        }
        return BulkLoadHandle.of("session_replication_role=replica");
    }

    /**
     * Restores {@code session_replication_role = origin}, re-enabling trigger and foreign-key firing
     * for the session before the connection is returned to the pool.
     *
     * @param connection the same connection passed to {@link #disableConstraints}
     * @param database   the database metadata (unused by this mechanism)
     * @param handle     the handle returned by {@link #disableConstraints}
     * @throws SQLException if the statement fails
     */
    @Override
    public void enableConstraints(Connection connection, Database database, BulkLoadHandle handle) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET session_replication_role = origin");
        }
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // PostgreSQL reports both bit/bit(n) and boolean as JDBCType.BIT, distinguished by
        // type name. The generic default would emit an integer for a single bit, which the
        // server rejects for a bit column.
        registry.put(JDBCType.BIT, (column, random) -> {
            if ("bool".equalsIgnoreCase(column.typeName())) {
                return new BooleanGenerator.Builder(random).build();
            }
            return new BitStringGenerator.Builder(random).size(bitStringSize(column)).build();
        });

        // Arrays are dispatched on the driver-reported element type name.
        registry.put(JDBCType.ARRAY, (column, random) -> {
            String typeName = column.typeName();
            if ("_text".equalsIgnoreCase(typeName)) {
                return new StringArrayGenerator.Builder(random).build();
            } else if ("_int8".equalsIgnoreCase(typeName) || "_int4".equalsIgnoreCase(typeName) || "_int2".equalsIgnoreCase(typeName)) {
                return new IntegerArrayGenerator.Builder(random).build();
            }
            throw new UnsupportedOperationException("Data Type [" + typeName + "] for ARRAY not supported");
        });

        // xml surfaces as its own JDBC type.
        registry.put(JDBCType.SQLXML, (column, random) -> new XmlGenerator.Builder(random).build());

        // Most PostgreSQL extension types surface as OTHER, dispatched on the type name.
        registry.put(JDBCType.OTHER, (column, random) -> {
            String typeName = column.typeName() == null ? "" : column.typeName().toLowerCase();
            return switch (typeName) {
                case "uuid" -> new UUIDGenerator.Builder(random).build();
                case "json", "jsonb" -> new JsonbGenerator.Builder(random).build();
                case "inet" -> new InetGenerator.Builder(random).build();
                case "cidr" -> new CidrGenerator.Builder(random).build();
                case "macaddr" -> new MacAddressGenerator.Builder(random).build();
                case "macaddr8" -> new MacAddressGenerator.Builder(random).octets(8).build();
                case "interval" -> new IntervalGenerator.Builder(random).build();
                case "varbit" -> new BitStringGenerator.Builder(random).size(bitStringSize(column)).build();
                default -> throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for OTHER not supported");
            };
        });
    }

    /**
     * Bit-string length to generate, falling back to a small default when the column reports
     * no usable length (e.g. unbounded {@code bit varying}).
     */
    private static int bitStringSize(Column column) {
        Integer maxSize = column.maxSize();
        if (maxSize == null || maxSize <= 0 || maxSize > 256) {
            return 8;
        }
        return maxSize;
    }
}
