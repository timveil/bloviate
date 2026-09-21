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
import io.bloviate.gen.BitGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.LongGenerator;
import io.bloviate.gen.SimpleStringGenerator;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;

/**
 * MySQL-specific {@link DatabaseSupport}.
 *
 * <p>MySQL's JDBC driver maps most types onto standard JDBC types that the cross-database
 * defaults already handle. Two need MySQL-specific treatment:
 *
 * <ul>
 *   <li>{@code JSON}, which the driver reports as {@link JDBCType#LONGVARCHAR} (type name
 *       {@code JSON}); a random string would fail the server's JSON validation, so those columns
 *       are routed to a JSON generator while ordinary text columns stay on the default string
 *       generator.</li>
 *   <li>{@code BIT(n)}, which in MySQL holds an <em>n-bit unsigned integer</em>, not the bit
 *       <em>string</em> the SQL standard (and PostgreSQL) define. The cross-database default sends
 *       a string of {@code '0'}/{@code '1'} characters, which MySQL reads as that many bytes and
 *       rejects with {@code Data too long} for any {@code n} above 1. This support sends a number
 *       bounded by the column's declared width instead. {@code TINYINT(1)}, and therefore
 *       {@code BOOLEAN}, arrives as JDBC {@code BIT} too &mdash; see
 *       {@code declaredBitColumn} for how the two are told apart.</li>
 * </ul>
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

    /** The widest MySQL {@code BIT} column: {@code BIT(64)}. */
    private static final int MAX_BIT_WIDTH = 64;

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

        // A MySQL BIT(n) column holds an n-bit unsigned integer, so it takes a number, not the
        // '0'/'1' bit string the standard defines and the cross-database default produces.
        //
        // JDBC BIT is not only BIT(n) here: with tinyInt1isBit on (the default), both drivers report
        // TINYINT(1) — and therefore BOOLEAN — as JDBC BIT too. Connector/J names that column
        // TINYINT, so it is recognised and kept at a single bit; see declaredBitColumn for what
        // MariaDB's driver does instead.
        registry.put(JDBCType.BIT, (column, random) -> {
            int bits = declaredBitColumn(column) ? bitWidth(column) : 1;
            if (bits <= 1) {
                return new BitGenerator.Builder(random).build();
            }
            return new LongGenerator.Builder(random).start(0).end(exclusiveBound(bits)).build();
        });

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

    /**
     * Whether the column is a real {@code BIT} column rather than a {@code TINYINT(1)} the driver
     * also reports as JDBC {@code BIT}.
     *
     * <p>Connector/J names the first {@code "BIT"} and the second {@code "TINYINT"} (and sizes it 1),
     * so the two are told apart there. MariaDB's driver reports the declared type with its
     * parenthesised width stripped, which makes a {@code TINYINT(1)} {@code "BIT"} as well, and gives
     * it a {@code COLUMN_SIZE} of 3 &mdash; {@code TINYINT}'s numeric precision. On MariaDB a
     * {@code BOOLEAN} column is therefore filled as a 3-bit number, {@code 0..7}: every one of those
     * values stores and reads back as the column's truth value (a {@code TINYINT(1)} holds any byte,
     * and everything but 0 is true), so the ambiguity costs range, not correctness, and the metadata
     * offers nothing that would resolve it.
     *
     * <p>A driver that names the column something else again falls to the single-bit generator, whose
     * values every one of these columns accepts.
     */
    private static boolean declaredBitColumn(Column column) {
        String typeName = column.typeName();
        return typeName != null && typeName.toUpperCase(Locale.ROOT).startsWith("BIT");
    }

    /**
     * The declared width in bits of a {@code BIT} column, from {@code COLUMN_SIZE}.
     *
     * <p>Connector/J reports {@code BIT(n)}'s {@code COLUMN_SIZE} as the bit count {@code n}. The
     * value is clamped rather than trusted outright: {@code COLUMN_SIZE} is nullable metadata, and a
     * width outside {@code [1, 64]} cannot describe a MySQL {@code BIT} column. Clamping down is the
     * safe direction — it narrows the range generated, where trusting an over-reported width would
     * overflow the column.
     */
    private static int bitWidth(Column column) {
        Integer maxSize = column.maxSize();
        return maxSize == null ? 1 : Math.clamp(maxSize, 1, MAX_BIT_WIDTH);
    }

    /**
     * The exclusive upper bound for an {@code n}-bit value, {@code 2^n}.
     *
     * <p>{@code BIT(63)} and {@code BIT(64)} reach past {@code Long.MAX_VALUE}, which
     * {@link LongGenerator} cannot express, so they draw from the widest range it has. Every value it
     * yields still fits the column; only the top of its range goes unused.
     */
    private static long exclusiveBound(int bits) {
        return bits >= Long.SIZE - 1 ? Long.MAX_VALUE : 1L << bits;
    }
}
