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

import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresFillerTest extends BasePostgresTest {

    @Test
    void fillTPCC() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), tableConfigurations);
        fillDatabase("create_tpcc.postgres.sql", configuration);
    }

    @Test
    void fillTPCCWithConfigs() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_WAREHOUSE, Constants.TPCC_NUM_WAREHOUSES));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ITEM, Constants.TPCC_NUM_ITEMS));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_STOCK, Constants.TPCC_NUM_STOCK));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_DISTRICT, Constants.TPCC_NUM_DISTRICTS));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_CUSTOMER, Constants.TPCC_NUM_CUSTOMERS));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_HISTORY, Constants.TPCC_NUM_HISTORY));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_OPEN_ORDER, Constants.TPCC_NUM_OPEN_ORDER));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_NEW_ORDER, Constants.TPCC_NUM_NEW_ORDER));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ORDER_LINE, Constants.TPCC_NUM_ORDER_LINE));

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), tableConfigurations);
        fillDatabase("create_tpcc.postgres.sql", configuration);

    }

    @Test
    void fillWithColumnConfiguration() throws SQLException {

        long rows = 25;

        // override the "code" column with a generator that always produces 7 (start inclusive, end exclusive)
        Set<ColumnConfiguration> columnConfigurations = Set.of(
                new ColumnConfiguration("code", random -> new IntegerGenerator.Builder(random).start(7).end(8).build()));

        Set<TableConfiguration> tableConfigurations = Set.of(
                new TableConfiguration("widget", rows, columnConfigurations));

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, rows, new PostgresSupport(), tableConfigurations);

        fillDatabase("create_column_config_test.postgres.sql", configuration, connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select code, label from widget")) {

                long actualRows = 0;
                while (resultSet.next()) {
                    actualRows++;

                    // overridden column: always 7
                    assertEquals(7, resultSet.getInt("code"));

                    // non-configured column: still auto-generated within the varchar(50) bound
                    String label = resultSet.getString("label");
                    assertNotNull(label);
                    assertTrue(label.length() <= 50, "label exceeded column size: " + label);
                }

                assertEquals(rows, actualRows, "row count should match the table configuration");
            }
        });
    }

    @Test
    void fillTestTables() throws SQLException {
        // exercises the PostgreSQL vendor types (uuid, json/jsonb, inet/cidr/macaddr,
        // bit/varbit, interval, arrays, xml); any unsupported type or invalid generated
        // value would make the fill itself throw
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new PostgresSupport(), new HashSet<>());
        fillDatabase("create_tables.postgres.sql", configuration, connection -> {
            assertRowCount(connection, "uuid_table", 5);
            assertRowCount(connection, "json_table", 5);
            assertRowCount(connection, "network_table", 5);
            assertRowCount(connection, "bit_table", 5);
            assertRowCount(connection, "interval_table", 5);
            assertRowCount(connection, "array_table", 5);
            assertRowCount(connection, "doc_table", 5);
            assertRowCount(connection, "standard_table", 5);
        });
    }

    @Test
    void fillTPCCWithColumnConfigs() throws SQLException {

        int w = 2;          // warehouses
        int i = 10;         // items
        int d = 2;          // districts per warehouse
        int c = 3;          // customers (and orders) per district
        int minLines = 5;   // minimum order lines per order
        int maxLines = 15;  // maximum order lines per order
        int newOrders = 2;  // most-recent orders per district mirrored into new_order

        DatabaseConfiguration configuration = new DatabaseConfiguration(
                128, 10, new PostgresSupport(), TPCCConfiguration.build(w, i, d, c, minLines, maxLines, newOrders));

        fillDatabase("create_tpcc.postgres.sql", configuration, connection -> {
            // cardinalities of the dense cartesian-product fill; if any composite key
            // collided or any FK were invalid, the fill itself would have thrown
            assertRowCount(connection, "warehouse", w);
            assertRowCount(connection, "item", i);
            assertRowCount(connection, "stock", (long) w * i);
            assertRowCount(connection, "district", (long) w * d);
            assertRowCount(connection, "customer", (long) w * d * c);
            assertRowCount(connection, "history", (long) w * d * c);
            assertRowCount(connection, "open_order", (long) w * d * c);
            assertRowCount(connection, "new_order", (long) w * d * newOrders);
            // order_line has a variable number of rows per order; its total is asserted
            // relationally (== sum of o_ol_cnt) inside assertTpccColumnFidelity

            assertTpccColumnFidelity(connection, c, minLines, maxLines, newOrders);
        });
    }
}