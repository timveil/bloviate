package io.bloviate.db;

public class Constants {
    private static final int TPCC_DISTRICTS_PER_WAREHOUSE = 2;
    private static final int TPCC_CUSTOMERS_PER_DISTRICT = 3;

    public static int TPCC_NUM_WAREHOUSES = 1;
    public static int TPCC_NUM_ITEMS = 100;
    public static int TPCC_NUM_STOCK = 100;
    public static int TPCC_NUM_DISTRICTS = TPCC_NUM_WAREHOUSES * TPCC_DISTRICTS_PER_WAREHOUSE;
    public static int TPCC_NUM_CUSTOMERS = TPCC_NUM_DISTRICTS * TPCC_CUSTOMERS_PER_DISTRICT;
    public static int TPCC_NUM_HISTORY = TPCC_NUM_CUSTOMERS;
    public static int TPCC_NUM_OPEN_ORDER = TPCC_NUM_CUSTOMERS;
    public static int TPCC_NUM_NEW_ORDER = TPCC_NUM_CUSTOMERS;
    public static int TPCC_NUM_ORDER_LINE = TPCC_NUM_CUSTOMERS * 10;

    public static String TPCC_WAREHOUSE = "warehouse";
    public static String TPCC_ITEM = "item";
    public static String TPCC_STOCK = "stock";
    public static String TPCC_DISTRICT = "district";
    public static String TPCC_CUSTOMER = "customer";
    public static String TPCC_HISTORY = "history";
    public static String TPCC_OPEN_ORDER = "open_order";
    public static String TPCC_NEW_ORDER = "new_order";
    public static String TPCC_ORDER_LINE = "order_line";
}
