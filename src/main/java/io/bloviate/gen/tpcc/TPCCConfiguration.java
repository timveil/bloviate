package io.bloviate.gen.tpcc;

import io.bloviate.db.ColumnConfiguration;
import io.bloviate.db.TableConfiguration;
import io.bloviate.gen.*;

import java.util.HashSet;
import java.util.Set;

public class TPCCConfiguration {

    public static Set<TableConfiguration> build(int scaleFactor) {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();

        Set<ColumnConfiguration> itemColumnConfiguration = new HashSet<>();
        itemColumnConfiguration.add(new ColumnConfiguration("i_id", new SequentialIntegerGenerator.Builder(1, 100000).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_im_id", new IntegerGenerator.Builder().start(1).end(10000).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_name", new VariableStringGenerator.Builder().start(14).end(24).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_price", new DoubleGenerator.Builder().start(1d).end(100d).maxDigits(2).build()));
        itemColumnConfiguration.add(new ColumnConfiguration("i_data", new DataColumnGenerator()));

        Set<ColumnConfiguration> warehouseColumnConfiguration = new HashSet<>();
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_id", new SequentialIntegerGenerator.Builder(1, scaleFactor).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_name", new VariableStringGenerator.Builder().start(6).end(10).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_state", new SimpleStringGenerator.Builder().size(2).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_zip", new ZipCodeGenerator()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_tax", new DoubleGenerator.Builder().start(0.0000d).end(0.2000d).maxDigits(4).build()));
        warehouseColumnConfiguration.add(new ColumnConfiguration("w_ytd", new StaticFloatGenerator.Builder().value(300000f).build()));

        Set<ColumnConfiguration> stockColumnConfiguration = new HashSet<>();
        stockColumnConfiguration.add(new ColumnConfiguration("s_i_id", new SequentialIntegerGenerator.Builder(1, 100000).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_quantity", new IntegerGenerator.Builder().start(10).end(100).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_ytd", new StaticFloatGenerator.Builder().value(0f).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_order_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_remote_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        stockColumnConfiguration.add(new ColumnConfiguration("s_data", new DataColumnGenerator()));

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


        Set<ColumnConfiguration> historyColumnConfiguration = new HashSet<>();
        historyColumnConfiguration.add(new ColumnConfiguration("h_date", new CurrentSqlTimestampGenerator()));
        historyColumnConfiguration.add(new ColumnConfiguration("h_amount", new StaticDoubleGenerator.Builder().value(10.00d).build()));
        historyColumnConfiguration.add(new ColumnConfiguration("h_data", new VariableStringGenerator.Builder().start(12).end(24).build()));

        Set<ColumnConfiguration> orderColumnConfiguration = new HashSet<>();
        historyColumnConfiguration.add(new ColumnConfiguration("o_entry_d", new CurrentSqlTimestampGenerator()));
        //historyColumnConfiguration.add(new ColumnConfiguration("o_carrier_id", null));
        historyColumnConfiguration.add(new ColumnConfiguration("o_ol_cnt", new IntegerGenerator.Builder().start(5).end(15).build()));
        historyColumnConfiguration.add(new ColumnConfiguration("o_all_local", new StaticIntegerGenerator.Builder().value(1).build()));

        Set<ColumnConfiguration> orderLineColumnConfiguration = new HashSet<>();
        //orderLineColumnConfiguration.add(new ColumnConfiguration("ol_number", null));
        orderLineColumnConfiguration.add(new ColumnConfiguration("ol_i_id", new IntegerGenerator.Builder().start(1).end(100000).build())); // todo; i think this may cause problems
        //orderLineColumnConfiguration.add(new ColumnConfiguration("ol_delivery_d", null));
        orderLineColumnConfiguration.add(new ColumnConfiguration("ol_quantity", new StaticIntegerGenerator.Builder().value(5).build()));
        //orderLineColumnConfiguration.add(new ColumnConfiguration("ol_amount", null));
        orderLineColumnConfiguration.add(new ColumnConfiguration("ol_dist_info", new SimpleStringGenerator.Builder().size(24).build()));

        int items = 100000;
        int stock = scaleFactor * items;
        int districts = scaleFactor * 10;
        int customers = districts * 3000;
        int history = customers * 1;
        int orders = districts * 3000;

        tableConfigurations.add(new TableConfiguration("warehouse", scaleFactor, warehouseColumnConfiguration));
        tableConfigurations.add(new TableConfiguration("item", items, itemColumnConfiguration));
        tableConfigurations.add(new TableConfiguration("stock", stock, stockColumnConfiguration));
        tableConfigurations.add(new TableConfiguration("district", districts, districtColumnConfiguration));
        tableConfigurations.add(new TableConfiguration("customer", customers, customerColumnConfiguration));
        tableConfigurations.add(new TableConfiguration("history", history, historyColumnConfiguration));
        tableConfigurations.add(new TableConfiguration("order", orders, orderColumnConfiguration));
        //tableConfigurations.add(new TableConfiguration("new_order", Constants.TPCC_NUM_NEW_ORDER, null));
        //tableConfigurations.add(new TableConfiguration("order_line", Constants.TPCC_NUM_ORDER_LINE, orderLineColumnConfiguration));

        return tableConfigurations;
    }

}
