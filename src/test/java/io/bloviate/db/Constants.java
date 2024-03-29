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

public class Constants {
    private static final int TPCC_DISTRICTS_PER_WAREHOUSE = 5;
    private static final int TPCC_CUSTOMERS_PER_DISTRICT = 10;

    public static final int TPCC_NUM_WAREHOUSES = 1;
    public static final int TPCC_NUM_ITEMS = 100;
    public static final int TPCC_NUM_STOCK = 100;
    public static final int TPCC_NUM_DISTRICTS = TPCC_NUM_WAREHOUSES * TPCC_DISTRICTS_PER_WAREHOUSE;
    public static final int TPCC_NUM_CUSTOMERS = TPCC_NUM_DISTRICTS * TPCC_CUSTOMERS_PER_DISTRICT;
    public static final int TPCC_NUM_HISTORY = TPCC_NUM_CUSTOMERS;
    public static final int TPCC_NUM_OPEN_ORDER = TPCC_NUM_CUSTOMERS;
    public static final int TPCC_NUM_NEW_ORDER = TPCC_NUM_CUSTOMERS;
    public static final int TPCC_NUM_ORDER_LINE = TPCC_NUM_CUSTOMERS * 10;

    public static final String TPCC_WAREHOUSE = "warehouse";
    public static final String TPCC_ITEM = "item";
    public static final String TPCC_STOCK = "stock";
    public static final String TPCC_DISTRICT = "district";
    public static final String TPCC_CUSTOMER = "customer";
    public static final String TPCC_HISTORY = "history";
    public static final String TPCC_OPEN_ORDER = "open_order";
    public static final String TPCC_NEW_ORDER = "new_order";
    public static final String TPCC_ORDER_LINE = "order_line";
}
