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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the issue #447 reproducibility-under-parallelism guarantee: a parallel fill
 * ({@link DatabaseFiller.Builder#Builder(javax.sql.DataSource, DatabaseConfiguration)} with
 * {@link DatabaseFiller.Builder#threads(int)}) produces byte-for-byte the same data as the
 * sequential single-connection fill for the same seed.
 *
 * <p>A TPC-C schema is used deliberately: its foreign keys span several topological levels, so the
 * parallel walk genuinely fans out and barriers between levels, exercising the path that must stay
 * referentially valid and deterministic.
 */
class PostgresParallelFillTest extends BaseDatabaseTestCase {

    @Test
    void parallelFillMatchesSequentialFill() throws SQLException {
        Set<TableConfiguration> tables = TPCCConfiguration.build(2, 200, 10, 30, 5, 15);
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
                Map<String, List<String>> sequentialDump;

                // sequential fill on a single caller-managed connection (the default path)
                try (Connection connection = dataSource.getConnection()) {
                    new DatabaseFiller.Builder(connection, configuration).build().fill();
                    tableNames = tableNames(connection);
                    sequentialDump = dump(connection, tableNames);
                    truncateAll(connection, tableNames);
                }

                // parallel fill from the pool, four workers per topological level
                new DatabaseFiller.Builder(dataSource, configuration).threads(4).build().fill();

                try (Connection connection = dataSource.getConnection()) {
                    Map<String, List<String>> parallelDump = dump(connection, tableNames);
                    for (String table : tableNames) {
                        assertEquals(sequentialDump.get(table), parallelDump.get(table),
                                "table [" + table + "] must be identical for sequential vs parallel fill");
                    }
                }
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
     * Dumps each table ordered by every column, so the comparison is a canonical row multiset that
     * is independent of physical insert order (which a parallel fill may legitimately change).
     *
     * <p>Temporal columns are deliberately excluded: the default date/time/timestamp generators
     * anchor their range to {@code Instant.now()} at construction, so their values carry wall-clock
     * jitter and differ between <em>any</em> two fills run at different moments — sequential or
     * parallel alike. Every other column is purely seed-derived, so comparing them is the real test
     * that parallelism does not change the generated data.
     */
    private static Map<String, List<String>> dump(Connection connection, List<String> tables) throws SQLException {
        Map<String, List<String>> dump = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                List<Integer> columns = deterministicColumns(statement, table);

                // a table with nothing but wall-clock columns has no seed-derived data to compare
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

    /** The 1-based indexes of a table's columns, excluding the wall-clock-dependent temporal types. */
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
