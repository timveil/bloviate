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

import io.bloviate.ext.CockroachDBSupport;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

class CockroachDBFillerTest extends BaseCockroachTest {

    @Test
    void fillTPCC() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_tpcc.cockroachdb.sql", configuration);
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

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_tpcc.cockroachdb.sql", configuration);

    }

    @Test
    void fillTPCCWithColumnConfigs() throws SQLException {

        int w = 2;
        int i = 10;
        int d = 2;
        int c = 3;
        int l = 2;

        DatabaseConfiguration configuration = new DatabaseConfiguration(
                128, 10, new CockroachDBSupport(), TPCCConfiguration.build(w, i, d, c, l));

        fillDatabase("create_tpcc.cockroachdb.sql", configuration, connection -> {
            assertRowCount(connection, "warehouse", w);
            assertRowCount(connection, "item", i);
            assertRowCount(connection, "stock", (long) w * i);
            assertRowCount(connection, "district", (long) w * d);
            assertRowCount(connection, "customer", (long) w * d * c);
            assertRowCount(connection, "history", (long) w * d * c);
            assertRowCount(connection, "open_order", (long) w * d * c);
            assertRowCount(connection, "new_order", (long) w * d * c);
            assertRowCount(connection, "order_line", (long) w * d * c * l);

            assertCount(connection, "select count(*) from customer where c_credit not in ('GC','BC')", 0);
            assertCount(connection, "select count(*) from customer where c_zip not like '____11111'", 0);
        });
    }

    @Test
    void fillTestTables() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_tables.cockroachdb.sql", configuration);
    }

    @Test
    void fillAuctionmark() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_auctionmark.cockroachdb.sql", configuration);
    }

    @Test
    void fillWikipedia() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_wikipedia.cockroachdb.sql", configuration);
    }

    @Test
    void fillSeats() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_seats.cockroachdb.sql", configuration);
    }

    @Test
    void fillTwitter() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new CockroachDBSupport(), tableConfigurations);
        fillDatabase("create_twitter.cockroachdb.sql", configuration);
    }

}