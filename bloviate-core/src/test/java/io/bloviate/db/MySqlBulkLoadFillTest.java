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
import io.bloviate.ext.BulkLoadHandle;
import io.bloviate.ext.BulkLoadUnsupportedException;
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
import java.util.HashSet;
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
 * <p>The load-bearing guarantee — and the reason bulk loading is safe — is that an unordered bulk fill
 * produces <strong>byte-for-byte identical</strong> data to a traditional dependency-ordered fill of
 * the same seed. This is asserted both unpartitioned and partitioned. The remaining cases cover the
 * fallback behaviors (unsupported support, privilege failure, and the single-connection path), each of
 * which must still produce a correct, fully-populated database.
 *
 * <p>All cases share one container for speed, truncating between fills.
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
    private static final int PARTITIONS = 3;
    private static final Set<String> PARTITIONED = Set.of("customer", "order_line");

    private static final long CUSTOMERS = (long) W * D * C;

    @Test
    void bulkPathsMatchOrderedAndFallBackCorrectly() throws SQLException {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);
        Set<TableConfiguration> partitioned = withPartitions(tables, PARTITIONS, PARTITIONED);

        try (MySQLContainer<?> database = new MySQLContainer<>("mysql:9.7")
                .withConfigurationOverride("mysql-conf")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedStatements", "true")
                .withInitScript("create_tpcc.mysql.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database)) {

                List<String> tableNames;
                try (Connection connection = dataSource.getConnection()) {
                    tableNames = tableNames(connection);
                }

                // 1) headline: unpartitioned ordered vs bulk must be byte-identical
                Map<String, List<String>> orderedDump = fillAndDump(dataSource, config(tables, BulkLoadStrategy.ordered()), tableNames);
                Map<String, List<String>> bulkDump = fillAndDump(dataSource, config(tables, BulkLoadStrategy.unorderedBulk()), tableNames);
                assertIdentical(orderedDump, bulkDump, tableNames, "unpartitioned ordered vs bulk");
                assertForeignKeyChecksRestored(dataSource);

                // 2) partitioned ordered vs partitioned bulk (same partition count) must be byte-identical
                Map<String, List<String>> pOrdered = fillAndDump(dataSource, config(partitioned, BulkLoadStrategy.ordered()), tableNames);
                Map<String, List<String>> pBulk = fillAndDump(dataSource, config(partitioned, BulkLoadStrategy.unorderedBulk()), tableNames);
                assertIdentical(pOrdered, pBulk, tableNames, "partitioned ordered vs bulk");

                // 3) bulk requested but the support reports no bulk capability -> ordered fallback, still correct
                DatabaseConfiguration unsupported = new DatabaseConfiguration(
                        256, 0, new NoBulkSupport(), tables, 42L, null, BulkLoadStrategy.unorderedBulk());
                fill(dataSource, unsupported);
                assertCustomerCount(dataSource);

                // 4) bulk supported but disabling constraints fails -> probe catches, ordered fallback, still correct
                DatabaseConfiguration privilegeDenied = new DatabaseConfiguration(
                        256, 0, new DenyDisableSupport(), tables, 42L, null, BulkLoadStrategy.unorderedBulk());
                fill(dataSource, privilegeDenied);
                assertCustomerCount(dataSource);

                // 5) single-connection path ignores bulk with a warning and fills in dependency order
                truncate(dataSource, tableNames);
                try (Connection connection = dataSource.getConnection()) {
                    new DatabaseFiller.Builder(connection, config(tables, BulkLoadStrategy.unorderedBulk())).build().fill();
                }
                assertCustomerCount(dataSource);
            }
        }
    }

    private static DatabaseConfiguration config(Set<TableConfiguration> tables, BulkLoadStrategy bulk) {
        return new DatabaseConfiguration(256, 0, new MySQLSupport(), tables, 42L, null, bulk);
    }

    /** A MySQL support that declares no bulk-load capability, exercising the unsupported fallback. */
    private static final class NoBulkSupport extends MySQLSupport {
        @Override
        public boolean supportsBulkLoad() {
            return false;
        }
    }

    /** A MySQL support whose constraint disabling always fails, exercising the privilege-failure fallback. */
    private static final class DenyDisableSupport extends MySQLSupport {
        @Override
        public BulkLoadHandle disableConstraints(Connection connection, Database database) throws SQLException {
            throw new BulkLoadUnsupportedException("simulated privilege failure", new SQLException("denied"));
        }
    }

    /** Runs a parallel fill, then dumps every table; truncates first so each fill starts clean. */
    private static Map<String, List<String>> fillAndDump(HikariDataSource dataSource, DatabaseConfiguration configuration, List<String> tableNames) throws SQLException {
        truncate(dataSource, tableNames);
        new DatabaseFiller.Builder(dataSource, configuration).threads(THREADS).build().fill();
        try (Connection connection = dataSource.getConnection()) {
            return dump(connection, tableNames);
        }
    }

    private static void fill(HikariDataSource dataSource, DatabaseConfiguration configuration) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            tableNamesTruncate(connection);
        }
        new DatabaseFiller.Builder(dataSource, configuration).threads(THREADS).build().fill();
    }

    private static void tableNamesTruncate(Connection connection) throws SQLException {
        truncate(connection, tableNames(connection));
    }

    private static void assertIdentical(Map<String, List<String>> a, Map<String, List<String>> b, List<String> tableNames, String label) {
        for (String table : tableNames) {
            assertEquals(a.get(table), b.get(table), "table [" + table + "] must be identical (" + label + ")");
        }
    }

    private static void assertCustomerCount(HikariDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertRowCount(connection, "customer", CUSTOMERS);
            // no order_line row references a missing order — proves referential validity after the fill
            assertCount(connection, "select count(*) from order_line l left join open_order o "
                    + "on l.ol_w_id = o.o_w_id and l.ol_d_id = o.o_d_id and l.ol_o_id = o.o_id "
                    + "where o.o_id is null", 0);
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

    /** Returns a copy of the table set with {@code partitions} applied to the named tables. */
    private static Set<TableConfiguration> withPartitions(Set<TableConfiguration> tables, int partitions, Set<String> names) {
        Set<TableConfiguration> result = new HashSet<>();
        for (TableConfiguration table : tables) {
            if (names.contains(table.tableName().toLowerCase())) {
                result.add(new TableConfiguration(table.tableName(), table.rowCount(), table.columnConfigurations(), partitions));
            } else {
                result.add(table);
            }
        }
        return result;
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

    private static void truncate(HikariDataSource dataSource, List<String> tables) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            truncate(connection, tables);
        }
    }

    private static void truncate(Connection connection, List<String> tables) throws SQLException {
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
