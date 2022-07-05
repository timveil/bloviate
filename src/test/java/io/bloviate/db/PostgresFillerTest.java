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
import io.bloviate.gen.tpcc.TPCCConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

class PostgresFillerTest extends BasePostgresTest {

    @Test
    void fillTPCC() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), null);
        fillDatabase("create_tpcc.postgres.sql", configuration, null);
    }

    @Test
    void fillTPCCWithTableConfigs() throws SQLException {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_WAREHOUSE, Constants.TPCC_NUM_WAREHOUSES, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ITEM, Constants.TPCC_NUM_ITEMS, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_STOCK, Constants.TPCC_NUM_STOCK, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_DISTRICT, Constants.TPCC_NUM_DISTRICTS, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_CUSTOMER, Constants.TPCC_NUM_CUSTOMERS, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_HISTORY, Constants.TPCC_NUM_HISTORY, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_OPEN_ORDER, Constants.TPCC_NUM_OPEN_ORDER, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_NEW_ORDER, Constants.TPCC_NUM_NEW_ORDER, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ORDER_LINE, Constants.TPCC_NUM_ORDER_LINE, null));

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), tableConfigurations);
        fillDatabase("create_tpcc.postgres.sql", configuration, null);

    }

    @Test
    void fillTPCCWithColumnConfigs() throws SQLException {

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), TPCCConfiguration.build(2));
        fillDatabase("create_tpcc.postgres.sql", configuration, new Validator() {
            @Override
            public void validate(Connection connection, DatabaseConfiguration configuration) throws SQLException {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select count(*) from item")) {
                    rs.next();
                    int count = rs.getInt(1);
                    Assertions.assertEquals(count, configuration.tableConfiguration("item").orElseThrow().rowCount());
                }

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select max(i_id), min(i_id), max(char_length(i_name)), min(character_length(i_name)), max(i_price), min(i_price) from item")) {
                    rs.next();
                    int maxId = rs.getInt(1);
                    int minId = rs.getInt(2);
                    int maxNameLength = rs.getInt(3);
                    int minNameLength = rs.getInt(4);
                    double maxPrice = rs.getDouble(5);
                    double minPrice = rs.getDouble(6);
                    Assertions.assertTrue(maxId <= 100000, String.format("max i_id is greater than %d", maxId));
                    Assertions.assertTrue(minId >= 1, String.format("min i_id is less than %d", minId));
                    Assertions.assertTrue(maxNameLength <= 24 , String.format("max i_name is greater than %d", maxNameLength));
                    Assertions.assertTrue(minNameLength >= 14 , String.format("min i_name is less than %d", minNameLength));
                    Assertions.assertTrue(maxPrice <= 100.00d, String.format("max i_price is greater than %3.2f", maxPrice));
                    Assertions.assertTrue(minPrice >= 1.00d, String.format("min i_price is less than %1.2f", minPrice));
                }

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select count(*) from warehouse")) {
                    rs.next();
                    int count = rs.getInt(1);
                    Assertions.assertEquals(count, configuration.tableConfiguration("warehouse").orElseThrow().rowCount());
                }

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select max(char_length(w_name)), min(character_length(w_name)), max(char_length(w_street_1)), min(character_length(w_street_1)), max(char_length(w_street_2)), min(character_length(w_street_2)), max(char_length(w_city)), min(character_length(w_city)), max(w_tax), min(w_tax) from warehouse")) {
                    rs.next();
                    int maxNameLength = rs.getInt(1);
                    int minNameLength = rs.getInt(2);
                    int maxStreet1Length = rs.getInt(3);
                    int minStreet1Length = rs.getInt(4);
                    int maxStreet2Length = rs.getInt(5);
                    int minStreet2Length = rs.getInt(6);
                    int maxCityLength = rs.getInt(7);
                    int minCityLength = rs.getInt(8);
                    double maxTax = rs.getDouble(9);
                    double minTax = rs.getDouble(10);
                    Assertions.assertTrue(maxNameLength <= 10 , String.format("max w_name is greater than %d", maxNameLength));
                    Assertions.assertTrue(minNameLength >= 6 , String.format("min w_name is less than %d", minNameLength));
                    Assertions.assertTrue(maxStreet1Length <= 20 , String.format("max w_street_1 is greater than %d", maxStreet1Length));
                    Assertions.assertTrue(minStreet1Length >= 10 , String.format("min w_street_1 is less than %d", minStreet1Length));
                    Assertions.assertTrue(maxStreet2Length <= 20 , String.format("max w_street_2 is greater than %d", maxStreet2Length));
                    Assertions.assertTrue(minStreet2Length >= 10 , String.format("min w_street_2 is less than %d", minStreet2Length));
                    Assertions.assertTrue(maxCityLength <= 20 , String.format("max w_city is greater than %d", maxCityLength));
                    Assertions.assertTrue(minCityLength >= 10 , String.format("min w_city is less than %d", minCityLength));
                    Assertions.assertTrue(maxTax <= 0.2000d, String.format("max i_price is greater than %.4f", maxTax));
                    Assertions.assertTrue(minTax >= 0.0000d, String.format("min i_price is less than %.4f", minTax));
                }
            }
        });

    }
}