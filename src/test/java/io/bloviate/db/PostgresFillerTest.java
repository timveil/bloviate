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
    void fillTPCCWithColumnConfigs() throws SQLException {

        int w = 2;   // warehouses
        int i = 10;  // items
        int d = 2;   // districts per warehouse
        int c = 3;   // customers (and orders) per district
        int l = 2;   // lines per order

        DatabaseConfiguration configuration = new DatabaseConfiguration(
                128, 10, new PostgresSupport(), TPCCConfiguration.build(w, i, d, c, l));

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
            assertRowCount(connection, "new_order", (long) w * d * c);
            assertRowCount(connection, "order_line", (long) w * d * c * l);

            // realistic value generators were applied
            assertCount(connection, "select count(*) from customer where c_credit not in ('GC','BC')", 0);
            assertCount(connection, "select count(*) from customer where c_zip not like '____11111'", 0);
            assertCount(connection, "select count(*) from customer where c_middle <> 'OE'", 0);
            assertCount(connection, "select count(*) from open_order where o_ol_cnt <> " + l, 0);
        });
    }
}