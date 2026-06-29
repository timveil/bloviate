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

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.MySQLSupport;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the {@link BulkLoadStrategy#unorderedBulk()} path on MySQL: it disables enforcement
 * ({@code FOREIGN_KEY_CHECKS=0}, {@code UNIQUE_CHECKS=0}) per worker session, fills every table at
 * once with no topological barrier, and restores the checks.
 *
 * <p>As on PostgreSQL, the load-bearing guarantee is that the unordered bulk fill produces
 * <strong>byte-for-byte identical</strong> data to a traditional dependency-ordered fill of the same
 * seed; this test asserts that table by table over a deep TPC-C chain. It also confirms no foreign-key
 * is left orphaned (an explicit anti-join) and that no pooled connection is left with
 * {@code FOREIGN_KEY_CHECKS} disabled.
 */
class MySqlBulkLoadFillTest extends BaseDatabaseTestCase {

    private static final int W = 2;
    private static final int I = 100;
    private static final int D = 5;
    private static final int C = 10;
    private static final int MIN_LINES = 5;
    private static final int MAX_LINES = 15;
    private static final int NEW_ORDERS = 5;
    private static final int THREADS = 4;

    @Test
    void bulkFillMatchesOrderedFillExactly() throws SQLException {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);

        DatabaseConfiguration ordered = new DatabaseConfiguration(
                256, 0, new MySQLSupport(), tables, 42L, null, BulkLoadStrategy.ordered());
        DatabaseConfiguration bulk = new DatabaseConfiguration(
                256, 0, new MySQLSupport(), tables, 42L, null, BulkLoadStrategy.unorderedBulk());

        try (MySQLContainer<?> database = new MySQLContainer<>("mysql:9.7")
                .withConfigurationOverride("mysql-conf")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedStatements", "true")
                .withInitScript("create_tpcc.mysql.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database)) {

                List<String> tableNames;
                Map<String, List<String>> orderedDump;

                // traditional dependency-ordered parallel fill — the reference result
                new DatabaseFiller.Builder(dataSource, ordered).threads(THREADS).build().fill();

                try (Connection connection = dataSource.getConnection()) {
                    tableNames = tableNames(connection);
                    assertRowCount(connection, "customer", (long) W * D * C);
                    orderedDump = dump(connection, tableNames);
                    truncateAll(connection, tableNames);
                }

                // unordered bulk fill — checks disabled, no barrier
                new DatabaseFiller.Builder(dataSource, bulk).threads(THREADS).build().fill();

                try (Connection connection = dataSource.getConnection()) {
                    assertRowCount(connection, "customer", (long) W * D * C);

                    Map<String, List<String>> bulkDump = dump(connection, tableNames);
                    for (String table : tableNames) {
                        assertEquals(orderedDump.get(table), bulkDump.get(table),
                                "table [" + table + "] must be identical between the ordered and bulk fills");
                    }

                    // no order_line row references a missing order (would be impossible if data is valid)
                    assertCount(connection, "select count(*) from order_line l left join open_order o "
                            + "on l.ol_w_id = o.o_w_id and l.ol_d_id = o.o_d_id and l.ol_o_id = o.o_id "
                            + "where o.o_id is null", 0);

                    // the bulk path restored each worker's session
                    assertForeignKeyChecksRestored(dataSource);
                }
            }
        }
    }

    /** Borrows a handful of connections and asserts each reports {@code @@SESSION.foreign_key_checks = 1}. */
    private static void assertForeignKeyChecksRestored(HikariDataSource dataSource) throws SQLException {
        for (int i = 0; i < THREADS + 1; i++) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select @@SESSION.foreign_key_checks")) {
                resultSet.next();
                assertEquals(1, resultSet.getLong(1),
                        "a pooled connection must not be left with foreign-key checks disabled");
            }
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        for (Table table : DatabaseUtils.getMetadata(connection).tables()) {
            names.add(table.name());
        }
        return names;
    }

    /**
     * Dumps each table ordered by every (non-temporal) column, producing a canonical row list
     * independent of the physical insert order the barrier-free bulk path may use.
     */
    private static Map<String, List<String>> dump(Connection connection, List<String> tables) throws SQLException {
        Map<String, List<String>> dump = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                List<Integer> columns = deterministicColumns(statement, table);
                if (columns.isEmpty()) {
                    dump.put(table, List.of());
                    continue;
                }

                StringJoiner order = new StringJoiner(",");
                for (int column : columns) {
                    order.add(String.valueOf(column));
                }

                List<String> rows = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery("select * from " + table + " order by " + order)) {
                    while (resultSet.next()) {
                        StringJoiner row = new StringJoiner("|");
                        for (int column : columns) {
                            row.add(String.valueOf(resultSet.getString(column)));
                        }
                        rows.add(row.toString());
                    }
                }
                dump.put(table, rows);
            }
        }
        return dump;
    }

    /** The 1-based indexes of a table's columns, excluding temporal types (which may use wall-clock time). */
    private static List<Integer> deterministicColumns(Statement statement, String table) throws SQLException {
        List<Integer> columns = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery("select * from " + table + " where false")) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                if (!isTemporal(metaData.getColumnType(i))) {
                    columns.add(i);
                }
            }
        }
        return columns;
    }

    private static boolean isTemporal(int sqlType) {
        return sqlType == Types.DATE
                || sqlType == Types.TIME
                || sqlType == Types.TIME_WITH_TIMEZONE
                || sqlType == Types.TIMESTAMP
                || sqlType == Types.TIMESTAMP_WITH_TIMEZONE;
    }

    private static void truncateAll(Connection connection, List<String> tables) throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tables) {
                statement.execute("TRUNCATE TABLE " + table);
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }
}
