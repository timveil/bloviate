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

import io.bloviate.ext.MySQLSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Issue #640: on MySQL the same seed and schema produced different DATE, TIME, DATETIME and
 * TIMESTAMP values depending on the JVM's default time zone, because the generators bound
 * {@code java.sql} temporals through the no-Calendar setters and the driver rendered them in that
 * zone. That breaks the seed-reproducibility invariant in {@code CONTRIBUTING.md}: nothing that
 * feeds generation may depend on the machine a fill runs on.
 *
 * <p>Both fills here use one container and one schema, with a fresh {@link DriverManager}
 * connection each time so Connector/J re-reads the JVM zone (it resolves it once per session, and a
 * pooled connection would keep the first one). Every dump is read back in a pinned session zone, so
 * the comparison sees what was stored rather than how this connection happens to render it.
 *
 * <p>The session-zone case asserts less, deliberately. MySQL's {@code TIMESTAMP} is an instant that
 * the server converts from the session zone on write and back to it on read, so its stored value
 * follows that zone by definition — a client cannot pin it, and Bloviate does not try. What it can
 * pin, and what this asserts, is that the zone-free types do not move.
 *
 * <p>{@link Isolated} because {@link TimeZone#setDefault} is global and this module runs its test
 * classes concurrently.
 */
@Isolated
class MySqlTemporalReproducibilityTest extends BaseDatabaseTestCase {

    private static final long ROWS = 25L;
    private static final long SEED = 42L;

    /** Two zones on opposite sides of UTC: a JVM-zone bind would put 14 hours between the dumps. */
    private static final String WEST = "America/New_York";
    private static final String EAST = "Asia/Tokyo";

    /** The zone-free columns, which nothing about a connection or a server may move. */
    private static final String WALL_CLOCK = "cast(d as char), cast(t as char), cast(dt as char)";
    private static final String EVERY_COLUMN = WALL_CLOCK + ", cast(ts as char)";

    @Test
    void temporalValuesDoNotFollowTheJvmOrSessionTimeZone() throws SQLException {
        TimeZone originalZone = TimeZone.getDefault();

        try (MySQLContainer<?> database = new MySQLContainer<>(TestImages.MYSQL)
                .withConfigurationOverride("mysql-conf")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedStatements", "true")
                .withInitScript("create_temporal.mysql.sql")) {

            database.start();

            List<String> west = fillAndDump(database, WEST, null, EVERY_COLUMN);
            List<String> east = fillAndDump(database, EAST, null, EVERY_COLUMN);

            assertFalse(west.isEmpty(), "the fixture must generate rows to compare");
            assertEquals(ROWS, west.size());
            assertEquals(west, east, "temporal values must not depend on the JVM's default time zone");

            // the same fill with the session on another zone: the zone-free types must not move
            List<String> shifted = fillAndDump(database, WEST, "+05:30", WALL_CLOCK);

            assertEquals(fillAndDump(database, WEST, null, WALL_CLOCK), shifted,
                    "DATE, TIME and DATETIME must not depend on the session time zone either");
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }

    /**
     * Fills the fixture from an empty table with the JVM in {@code jvmZone} and, if given, the
     * session on {@code sessionZone}; then dumps {@code columns} through a connection pinned to UTC.
     */
    private List<String> fillAndDump(MySQLContainer<?> database, String jvmZone, String sessionZone, String columns)
            throws SQLException {

        TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));

        DatabaseConfiguration configuration =
                new DatabaseConfiguration(128, ROWS, new MySQLSupport(), null, SEED);

        try (Connection connection = connect(database)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("truncate table temporal_types");
                if (sessionZone != null) {
                    statement.execute("set time_zone = '" + sessionZone + "'");
                }
            }

            new DatabaseFiller.Builder(connection, configuration).build().fill();
        }

        // read back through a connection of its own, pinned to UTC, so the dump shows what was
        // stored rather than how the writing session would render it
        List<String> rows = new ArrayList<>();
        try (Connection connection = connect(database)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set time_zone = '+00:00'");
            }
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "select " + columns + " from temporal_types order by id")) {
                while (resultSet.next()) {
                    StringBuilder row = new StringBuilder();
                    for (int column = 1; column <= resultSet.getMetaData().getColumnCount(); column++) {
                        row.append(column == 1 ? "" : "|").append(resultSet.getString(column));
                    }
                    rows.add(row.toString());
                }
            }
        }
        return rows;
    }

    private static Connection connect(MySQLContainer<?> database) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }
}
