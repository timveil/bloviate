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
import io.bloviate.gen.tpcc.CreditGenerator;
import io.bloviate.gen.tpcc.CustomerLastNameGenerator;
import io.bloviate.gen.tpcc.DataColumnGenerator;
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
        customerColumnConfiguration.add(new ColumnConfiguration("c_last", new CustomerLastNameGenerator())); //todo this is wrong
        customerColumnConfiguration.add(new ColumnConfiguration("c_middle", new StaticStringGenerator.Builder().value("OE").build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_first", new VariableStringGenerator.Builder().start(8).end(16).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_state", new SimpleStringGenerator.Builder().size(2).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_zip", new ZipCodeGenerator()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_phone", new SimpleStringGenerator.Builder().size(16).letters(false).numbers(true).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_since", new CurrentSqlTimestampGenerator()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_credit", new CreditGenerator()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_credit_lim", new StaticDoubleGenerator.Builder().value(50000.00d).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_discount", new DoubleGenerator.Builder().start(0.0000d).end(0.5000d).maxDigits(4).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_balance", new StaticDoubleGenerator.Builder().value(-10.00d).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_ytd_payment", new StaticDoubleGenerator.Builder().value(10.00d).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_payment_cnt", new StaticIntegerGenerator.Builder().value(1).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_delivery_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        customerColumnConfiguration.add(new ColumnConfiguration("c_data", new VariableStringGenerator.Builder().start(100).end(500).build()));

        Set<ColumnConfiguration> itemColumnConfiguration = new HashSet<>();
        itemColumnConfiguration.add(new ColumnConfiguration("i_im_id", new IntegerGenerator.Builder().start(1).end(10000).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_name", new VariableStringGenerator.Builder().start(14).end(24).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_price", new DoubleGenerator.Builder().start(1d).end(100d).maxDigits(2).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_data", new DataColumnGenerator()));

        Set<ColumnConfiguration> warehouseColumnConfiguration = new HashSet<>();
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_name", new VariableStringGenerator.Builder().start(6).end(10).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_state", new SimpleStringGenerator.Builder().size(2).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_zip", new ZipCodeGenerator()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_tax", new DoubleGenerator.Builder().start(0.0000d).end(0.2000d).maxDigits(4).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_ytd", new StaticFloatGenerator.Builder().value(300000f).build()));

        Set<ColumnConfiguration> stockColumnConfiguration = new HashSet<>();
        stockColumnConfiguration.add(new ColumnConfiguration("s_quantity", new IntegerGenerator.Builder().start(10).end(100).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_ytd", new StaticFloatGenerator.Builder().value(0f).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_order_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_remote_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_data",  new DataColumnGenerator()));

        Set<ColumnConfiguration> districtColumnConfiguration = new HashSet<>();
        districtColumnConfiguration.add(new ColumnConfiguration("d_name", new VariableStringGenerator.Builder().start(6).end(10).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_state", new SimpleStringGenerator.Builder().size(2).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_zip", new ZipCodeGenerator()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_tax", new DoubleGenerator.Builder().start(0.0000d).end(0.2000d).maxDigits(4).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_ytd", new StaticFloatGenerator.Builder().value(30000f).build()));
        districtColumnConfiguration.add(new ColumnConfiguration("d_next_o_id", new StaticIntegerGenerator.Builder().value(3001).build()));

        Set<ColumnConfiguration> historyColumnConfiguration = new HashSet<>();
        historyColumnConfiguration.add(new ColumnConfiguration("h_date", new CurrentSqlTimestampGenerator()));
        historyColumnConfiguration.add(new ColumnConfiguration("h_amount", new StaticDoubleGenerator.Builder().value(10.00d).build()));
        historyColumnConfiguration.add(new ColumnConfiguration("h_data", new VariableStringGenerator.Builder().start(12).end(20).build()));

        Set<ColumnConfiguration> orderColumnConfiguration = new HashSet<>();
        historyColumnConfiguration.add(new ColumnConfiguration("o_entry_d", new CurrentSqlTimestampGenerator()));
        //historyColumnConfiguration.add(new ColumnConfiguration("o_carrier_id", new CurrentSqlTimestampGenerator()));
        historyColumnConfiguration.add(new ColumnConfiguration("o_ol_cnt", new IntegerGenerator.Builder().start(5).end(15).build()));
        historyColumnConfiguration.add(new ColumnConfiguration("o_all_local", new StaticIntegerGenerator.Builder().value(1).build()));

        tableConfigurations.add(new TableConfiguration(Constants.TPCC_WAREHOUSE, Constants.TPCC_NUM_WAREHOUSES, warehouseColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ITEM, Constants.TPCC_NUM_ITEMS, itemColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_STOCK, Constants.TPCC_NUM_STOCK, stockColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_DISTRICT, Constants.TPCC_NUM_DISTRICTS, districtColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_CUSTOMER, Constants.TPCC_NUM_CUSTOMERS, customerColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_HISTORY, Constants.TPCC_NUM_HISTORY, historyColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_OPEN_ORDER, Constants.TPCC_NUM_OPEN_ORDER, orderColumnConfiguration));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_NEW_ORDER, Constants.TPCC_NUM_NEW_ORDER, null));
        tableConfigurations.add(new TableConfiguration(Constants.TPCC_ORDER_LINE, Constants.TPCC_NUM_ORDER_LINE, null));

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), tableConfigurations);
        fillDatabase("create_tpcc.postgres.sql", configuration);

    }
}