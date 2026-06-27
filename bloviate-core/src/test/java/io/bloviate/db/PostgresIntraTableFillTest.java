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
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

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
 * Verifies issue #466 intra-table parallelism: partitioning a single table's rows across workers
 * (configured via {@link TableConfiguration#partitions()} on the parallel
 * {@link DatabaseFiller.Builder#Builder(javax.sql.DataSource, DatabaseConfiguration)} +
 * {@link DatabaseFiller.Builder#threads(int)} path).
 *
 * <p>A TPC-C schema is used because it carries real foreign-key constraints and rich positional
 * correlations (composite keys, per-order line counts, the per-district {@code o_c_id} permutation,
 * the delivery-state prefix), so a partitioned fill that completes and passes
 * {@link #assertTpccColumnFidelity} proves the key and key-correlated columns stayed coherent — i.e.
 * partitioning preserved foreign-key validity and reproducibility.
 */
class PostgresIntraTableFillTest extends BaseDatabaseTestCase {

    private static final int W = 2;
    private static final int I = 200;
    private static final int D = 10;
    private static final int C = 30;
    private static final int MIN_LINES = 5;
    private static final int MAX_LINES = 15;
    private static final int NEW_ORDERS = 10;

    // partition a mix of an early parent (stock), a mid parent (customer) and the largest leaf
    // (order_line); all use positional key generators, so foreign keys stay valid under partitioning
    private static final Set<String> PARTITIONED_TABLES = Set.of("stock", "customer", "order_line");
    private static final int PARTITIONS = 4;

    @Test
    void intraTablePartitionedFillIsValidAndReproducible() throws SQLException {
        Set<TableConfiguration> tables = withPartitions(
                TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS), PARTITIONS, PARTITIONED_TABLES);
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(256, 0, new PostgresSupport(), tables, 42L);

        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_tpcc.postgres.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database)) {

                List<String> tableNames;
                Map<String, List<String>> firstDump;

                // first partitioned fill: four workers, three tables split into four row ranges each.
                // Completing at all means every foreign key resolved (the schema enforces them).
                new DatabaseFiller.Builder(dataSource, configuration).threads(4).build().fill();

                try (Connection connection = dataSource.getConnection()) {
                    tableNames = tableNames(connection);

                    // row counts are exactly what a sequential fill would produce (no rows lost or duplicated)
                    assertRowCount(connection, "warehouse", W);
                    assertRowCount(connection, "item", I);
                    assertRowCount(connection, "stock", (long) W * I);
                    assertRowCount(connection, "district", (long) W * D);
                    assertRowCount(connection, "customer", (long) W * D * C);
                    assertRowCount(connection, "open_order", (long) W * D * C);
                    assertRowCount(connection, "new_order", (long) W * D * NEW_ORDERS);

                    // positional / key-correlated columns stayed coherent across the partition boundaries
                    assertTpccColumnFidelity(connection, C, MIN_LINES, MAX_LINES, NEW_ORDERS);

                    firstDump = dump(connection, tableNames);
                    truncateAll(connection, tableNames);
                }

                // second partitioned fill with the same configuration must reproduce the first exactly
                new DatabaseFiller.Builder(dataSource, configuration).threads(4).build().fill();

                try (Connection connection = dataSource.getConnection()) {
                    Map<String, List<String>> secondDump = dump(connection, tableNames);
                    for (String table : tableNames) {
                        assertEquals(firstDump.get(table), secondDump.get(table),
                                "table [" + table + "] must be identical across two partitioned fills of the same config");
                    }
                }
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
     * Dumps each table ordered by every (non-temporal) column, producing a canonical row multiset
     * independent of physical insert order (which a partitioned fill may legitimately change).
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
            statement.execute("TRUNCATE " + String.join(", ", tables) + " CASCADE");
        }
    }
}
