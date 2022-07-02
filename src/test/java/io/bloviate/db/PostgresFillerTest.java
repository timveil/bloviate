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
import io.bloviate.gen.*;
import io.bloviate.gen.tpcc.CustomerLastNameGenerator;
import io.bloviate.gen.tpcc.ItemDataGenerator;
import io.bloviate.gen.tpcc.ZipCodeGenerator;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

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

        Set<ColumnConfiguration> customerColumnConfiguration = new HashSet<>();
        customerColumnConfiguration.add(new ColumnConfiguration("c_last", new CustomerLastNameGenerator()));

        Set<ColumnConfiguration> itemColumnConfiguration = new HashSet<>();
        itemColumnConfiguration.add(new ColumnConfiguration("i_im_id", new IntegerGenerator.Builder().start(1).end(10000).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_name", new VariableStringGenerator.Builder().start(14).end(24).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_price", new DoubleGenerator.Builder().start(1d).end(100d).maxDigits(2).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_data", new ItemDataGenerator()));

        Set<ColumnConfiguration> warehouseColumnConfiguration = new HashSet<>();
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_name", new VariableStringGenerator.Builder().start(6).end(10).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_state", new SimpleStringGenerator.Builder().size(2).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_zip", new ZipCodeGenerator()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_tax", new DoubleGenerator.Builder().start(0.0d).end(0.2d).maxDigits(4).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_ytd", new StaticFloatGenerator.Builder().value(300000f).build()));


        tableConfigurations.add(new TableConfiguration(Constants.TPCC_WAREHOUSE, Constants.TPCC_NUM_WAREHOUSES, warehouseColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ITEM, Constants.TPCC_NUM_ITEMS, itemColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_STOCK, Constants.TPCC_NUM_STOCK, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_DISTRICT, Constants.TPCC_NUM_DISTRICTS, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_CUSTOMER, Constants.TPCC_NUM_CUSTOMERS, customerColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_HISTORY, Constants.TPCC_NUM_HISTORY, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_OPEN_ORDER, Constants.TPCC_NUM_OPEN_ORDER, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_NEW_ORDER, Constants.TPCC_NUM_NEW_ORDER, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ORDER_LINE, Constants.TPCC_NUM_ORDER_LINE, null));

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), tableConfigurations);
        fillDatabase("create_tpcc.postgres.sql", configuration);

    }
}