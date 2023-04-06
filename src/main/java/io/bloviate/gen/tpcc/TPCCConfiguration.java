package io.bloviate.gen.tpcc;

import io.bloviate.db.ColumnConfiguration;
import io.bloviate.db.TableConfiguration;
import io.bloviate.gen.*;

import java.util.HashSet;
import java.util.Set;

public class TPCCConfiguration {

    public static final int DEFAULT_ITEM_COUNT = 100000;
    public static final int DEFAULT_DISTRICTS_PER_WAREHOUSE = 10;
    public static final int DEFAULT_CUSTOMERS_PER_DISTRICT = 3000;
    public static final int DEFAULT_HISTORY_PER_CUSTOMER = 1;
    public static final int DEFAULT_ORDERS_PER_DISTRICT = 3000;

    public static Set<TableConfiguration> build(double scaleFactor) {
        return build(scaleFactor, DEFAULT_ITEM_COUNT, DEFAULT_DISTRICTS_PER_WAREHOUSE, DEFAULT_CUSTOMERS_PER_DISTRICT, DEFAULT_HISTORY_PER_CUSTOMER, DEFAULT_ORDERS_PER_DISTRICT);
    }

    public static Set<TableConfiguration> build(double scaleFactor, int itemCount, int districtsPerWarehouse, int customersPerDistrict, int historyPerCustomer, int ordersPerDistrict) {

        int warehouses = (int) Math.max(1, scaleFactor);
        int items = Math.min((int) Math.round(itemCount * scaleFactor), DEFAULT_ITEM_COUNT);
        int stock = warehouses * items;
        int districts = warehouses * Math.min((int) Math.round(districtsPerWarehouse * scaleFactor), DEFAULT_DISTRICTS_PER_WAREHOUSE);
        int customers = districts * Math.min((int) Math.round(customersPerDistrict * scaleFactor), DEFAULT_CUSTOMERS_PER_DISTRICT);
        int history = customers * historyPerCustomer;
        int orders = districts * Math.min((int) Math.round(ordersPerDistrict * scaleFactor), DEFAULT_ORDERS_PER_DISTRICT);

        // todo: add nice logging about sizes

        Set<TableConfiguration> tableConfigurations = new HashSet<>();

        tableConfigurations.add(new TableConfiguration("warehouse", warehouses, null, warehouseConfigurations(warehouses)));
        tableConfigurations.add(new TableConfiguration("item", items, null, itemConfigurations(itemCount)));
        tableConfigurations.add(new TableConfiguration("stock", stock, null, stockConfigurations()));
        tableConfigurations.add(new TableConfiguration("district", districts, null, districtConfigurations()));
        tableConfigurations.add(new TableConfiguration("customer", customers, null, customerConfigurations()));
        tableConfigurations.add(new TableConfiguration("history", history, null, historyConfigurations()));
        tableConfigurations.add(new TableConfiguration("order", orders, null, orderConfigurations()));
        //tableConfigurations.add(new TableConfiguration("new_order", Constants.TPCC_NUM_NEW_ORDER, null));
        //tableConfigurations.add(new TableConfiguration("order_line", 0, orderLineConfigurations()));

        return tableConfigurations;
    }

    private static Set<ColumnConfiguration> orderLineConfigurations() {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        //orderLineColumnConfiguration.add(new ColumnConfiguration("ol_number", null));
        configurations.add(new ColumnConfiguration("ol_i_id", new IntegerGenerator.Builder().start(1).end(100000).build())); // todo; i think this may cause problems
        //orderLineColumnConfiguration.add(new ColumnConfiguration("ol_delivery_d", null));
        configurations.add(new ColumnConfiguration("ol_quantity", new StaticIntegerGenerator.Builder().value(5).build()));
        //orderLineColumnConfiguration.add(new ColumnConfiguration("ol_amount", null));
        configurations.add(new ColumnConfiguration("ol_dist_info", new SimpleStringGenerator.Builder().size(24).build()));
        return configurations;
    }

    private static Set<ColumnConfiguration> orderConfigurations() {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        configurations.add(new ColumnConfiguration("o_entry_d", new CurrentSqlTimestampGenerator()));
        //orderColumnConfiguration.add(new ColumnConfiguration("o_carrier_id", null));
        configurations.add(new ColumnConfiguration("o_ol_cnt", new IntegerGenerator.Builder().start(5).end(15).build()));
        configurations.add(new ColumnConfiguration("o_all_local", new StaticIntegerGenerator.Builder().value(1).build()));
        return configurations;
    }

    private static Set<ColumnConfiguration> historyConfigurations() {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        configurations.add(new ColumnConfiguration("h_date", new CurrentSqlTimestampGenerator()));
        configurations.add(new ColumnConfiguration("h_amount", new StaticDoubleGenerator.Builder().value(10.00d).build()));
        configurations.add(new ColumnConfiguration("h_data", new VariableStringGenerator.Builder().start(12).end(24).build()));
        return configurations;
    }

    private static Set<ColumnConfiguration> customerConfigurations() {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        configurations.add(new ColumnConfiguration("c_last", new CustomerLastNameGenerator())); //todo this is wrong
        configurations.add(new ColumnConfiguration("c_middle", new StaticStringGenerator.Builder().value("OE").build()));
        configurations.add(new ColumnConfiguration("c_first", new VariableStringGenerator.Builder().start(8).end(16).build()));
        configurations.add(new ColumnConfiguration("c_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("c_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("c_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("c_state", new SimpleStringGenerator.Builder().size(2).build()));
        configurations.add(new ColumnConfiguration("c_zip", new ZipCodeGenerator()));
        configurations.add(new ColumnConfiguration("c_phone", new SimpleStringGenerator.Builder().size(16).letters(false).numbers(true).build()));
        configurations.add(new ColumnConfiguration("c_since", new CurrentSqlTimestampGenerator()));
        configurations.add(new ColumnConfiguration("c_credit", new CreditGenerator()));
        configurations.add(new ColumnConfiguration("c_credit_lim", new StaticDoubleGenerator.Builder().value(50000.00d).build()));
        configurations.add(new ColumnConfiguration("c_discount", new DoubleGenerator.Builder().start(0.0000d).end(0.5000d).maxDigits(4).build()));
        configurations.add(new ColumnConfiguration("c_balance", new StaticDoubleGenerator.Builder().value(-10.00d).build()));
        configurations.add(new ColumnConfiguration("c_ytd_payment", new StaticDoubleGenerator.Builder().value(10.00d).build()));
        configurations.add(new ColumnConfiguration("c_payment_cnt", new StaticIntegerGenerator.Builder().value(1).build()));
        configurations.add(new ColumnConfiguration("c_delivery_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        configurations.add(new ColumnConfiguration("c_data", new VariableStringGenerator.Builder().start(100).end(500).build()));
        return configurations;
    }

    private static Set<ColumnConfiguration> districtConfigurations() {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        configurations.add(new ColumnConfiguration("d_name", new VariableStringGenerator.Builder().start(6).end(10).build()));
        configurations.add(new ColumnConfiguration("d_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("d_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("d_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("d_state", new SimpleStringGenerator.Builder().size(2).build()));
        configurations.add(new ColumnConfiguration("d_zip", new ZipCodeGenerator()));
        configurations.add(new ColumnConfiguration("d_tax", new DoubleGenerator.Builder().start(0.0000d).end(0.2000d).maxDigits(4).build()));
        configurations.add(new ColumnConfiguration("d_ytd", new StaticFloatGenerator.Builder().value(30000f).build()));
        configurations.add(new ColumnConfiguration("d_next_o_id", new StaticIntegerGenerator.Builder().value(3001).build()));
        return configurations;
    }

    private static Set<ColumnConfiguration> stockConfigurations() {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        //stockColumnConfiguration.add(new ColumnConfiguration("s_i_id", new SequentialIntegerGenerator.Builder(1, 100000).build()));
        configurations.add(new ColumnConfiguration("s_quantity", new IntegerGenerator.Builder().start(10).end(100).build()));
        configurations.add(new ColumnConfiguration("s_ytd", new StaticFloatGenerator.Builder().value(0f).build()));
        configurations.add(new ColumnConfiguration("s_order_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        configurations.add(new ColumnConfiguration("s_remote_cnt", new StaticIntegerGenerator.Builder().value(0).build()));
        configurations.add(new ColumnConfiguration("s_data", new DataColumnGenerator()));
        return configurations;
    }

    private static Set<ColumnConfiguration> warehouseConfigurations(int warehouseCount) {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        configurations.add(new ColumnConfiguration("w_id", new SequentialIntegerGenerator.Builder(1, warehouseCount).build()));
        configurations.add(new ColumnConfiguration("w_name", new VariableStringGenerator.Builder().start(6).end(10).build()));
        configurations.add(new ColumnConfiguration("w_street_1", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("w_street_2", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("w_city", new VariableStringGenerator.Builder().start(10).end(20).build()));
        configurations.add(new ColumnConfiguration("w_state", new SimpleStringGenerator.Builder().size(2).build()));
        configurations.add(new ColumnConfiguration("w_zip", new ZipCodeGenerator()));
        configurations.add(new ColumnConfiguration("w_tax", new DoubleGenerator.Builder().start(0.0000d).end(0.2000d).maxDigits(4).build()));
        configurations.add(new ColumnConfiguration("w_ytd", new StaticFloatGenerator.Builder().value(300000f).build()));
        return configurations;
    }

    private static Set<ColumnConfiguration> itemConfigurations(int itemCount) {
        Set<ColumnConfiguration> configurations = new HashSet<>();
        configurations.add(new ColumnConfiguration("i_id", new SequentialIntegerGenerator.Builder(1, itemCount).build()));
        configurations.add(new ColumnConfiguration("i_im_id", new IntegerGenerator.Builder().start(1).end(10000).build()));
        configurations.add(new ColumnConfiguration("i_name", new VariableStringGenerator.Builder().start(14).end(24).build()));
        configurations.add(new ColumnConfiguration("i_price", new DoubleGenerator.Builder().start(1d).end(100d).maxDigits(2).build()));
        configurations.add(new ColumnConfiguration("i_data", new DataColumnGenerator()));
        return configurations;
    }

}
