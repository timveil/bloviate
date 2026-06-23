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

package io.bloviate.gen.tpcc;

import io.bloviate.db.ColumnConfiguration;
import io.bloviate.db.ColumnGeneratorFactory;
import io.bloviate.db.TableConfiguration;
import io.bloviate.gen.CompositeKeyComponentGenerator;
import io.bloviate.gen.StaticIntegerGenerator;
import io.bloviate.gen.StaticStringGenerator;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds a complete set of {@link TableConfiguration}s for the TPC-C schema
 * (see {@code create_tpcc.*.sql}), suitable for passing to a
 * {@code DatabaseConfiguration}.
 *
 * <p>The composite primary/foreign keys are populated as a dense, row-ordered
 * cartesian product using {@link CompositeKeyComponentGenerator}, so each table
 * receives exactly its cartesian cardinality of rows with unique keys and valid
 * references. Realistic values (last names, zip codes, credit, i_data/s_data) use
 * the TPC-C generators in this package; remaining columns fall back to the
 * database's auto-detected generators.
 *
 * <p>Simplifications relative to a strict benchmark load: orders-per-district
 * equals customers-per-district (one order per customer), {@code new_order}
 * mirrors every order, and each order has a fixed number of lines.
 */
public final class TPCCConfiguration {

    public static final int DEFAULT_ITEMS = 100_000;
    public static final int DEFAULT_DISTRICTS_PER_WAREHOUSE = 10;
    public static final int DEFAULT_CUSTOMERS_PER_DISTRICT = 3_000;
    public static final int DEFAULT_LINES_PER_ORDER = 10;

    private TPCCConfiguration() {
    }

    /**
     * Builds a TPC-C configuration using the default per-warehouse cardinalities.
     *
     * @param warehouses the number of warehouses (the TPC-C scale factor)
     * @return the set of table configurations
     */
    public static Set<TableConfiguration> build(int warehouses) {
        return build(warehouses, DEFAULT_ITEMS, DEFAULT_DISTRICTS_PER_WAREHOUSE, DEFAULT_CUSTOMERS_PER_DISTRICT, DEFAULT_LINES_PER_ORDER);
    }

    /**
     * Builds a TPC-C configuration with explicit cardinalities (useful for tests).
     *
     * @param warehouses           number of warehouses (W)
     * @param items                number of items (I)
     * @param districtsPerWarehouse districts per warehouse (D)
     * @param customersPerDistrict  customers per district (C); also used as orders per district
     * @param linesPerOrder         order lines per order (L)
     * @return the set of table configurations
     */
    public static Set<TableConfiguration> build(int warehouses, int items, int districtsPerWarehouse, int customersPerDistrict, int linesPerOrder) {

        final int w = warehouses;
        final int i = items;
        final int d = districtsPerWarehouse;
        final int c = customersPerDistrict;
        final int o = customersPerDistrict; // one order per customer
        final int l = linesPerOrder;

        final long stockRows = (long) w * i;
        final long districtRows = (long) w * d;
        final long customerRows = (long) w * d * c;
        final long orderRows = (long) w * d * o;
        final long orderLineRows = (long) w * d * o * l;

        Set<TableConfiguration> tables = new HashSet<>();

        tables.add(new TableConfiguration("warehouse", w, set(
                key("w_id", 1, 1, w),
                col("w_zip", zip()))));

        tables.add(new TableConfiguration("item", i, set(
                key("i_id", 1, 1, i),
                col("i_data", data()))));

        tables.add(new TableConfiguration("stock", stockRows, set(
                key("s_w_id", 1, i, w),
                key("s_i_id", 1, 1, i),
                col("s_data", data()))));

        tables.add(new TableConfiguration("district", districtRows, set(
                key("d_w_id", 1, d, w),
                key("d_id", 1, 1, d),
                col("d_zip", zip()))));

        tables.add(new TableConfiguration("customer", customerRows, set(
                key("c_w_id", 1, (long) d * c, w),
                key("c_d_id", 1, c, d),
                key("c_id", 1, 1, c),
                col("c_zip", zip()),
                col("c_last", lastName()),
                col("c_credit", credit()),
                col("c_middle", staticString("OE")))));

        tables.add(new TableConfiguration("history", customerRows, set(
                key("h_c_w_id", 1, (long) d * c, w),
                key("h_c_d_id", 1, c, d),
                key("h_c_id", 1, 1, c),
                key("h_w_id", 1, (long) d * c, w),
                key("h_d_id", 1, c, d))));

        tables.add(new TableConfiguration("open_order", orderRows, set(
                key("o_w_id", 1, (long) d * o, w),
                key("o_d_id", 1, o, d),
                key("o_id", 1, 1, o),
                key("o_c_id", 1, 1, c),
                col("o_ol_cnt", staticInt(l)),
                col("o_all_local", staticInt(1)))));

        tables.add(new TableConfiguration("new_order", orderRows, set(
                key("no_w_id", 1, (long) d * o, w),
                key("no_d_id", 1, o, d),
                key("no_o_id", 1, 1, o))));

        tables.add(new TableConfiguration("order_line", orderLineRows, set(
                key("ol_w_id", 1, (long) d * o * l, w),
                key("ol_d_id", 1, (long) o * l, d),
                key("ol_o_id", 1, l, o),
                key("ol_number", 1, 1, l),
                key("ol_supply_w_id", 1, (long) d * o * l, w),
                key("ol_i_id", 1, 1, i))));

        return tables;
    }

    /**
     * A composite-key component: emits {@code start + ((row / repeat) % cycle)}.
     */
    private static ColumnConfiguration key(String name, int start, long repeat, int cycle) {
        return new ColumnConfiguration(name,
                random -> new CompositeKeyComponentGenerator.Builder(random).start(start).repeat(repeat).cycle(cycle).build());
    }

    private static ColumnConfiguration col(String name, ColumnGeneratorFactory factory) {
        return new ColumnConfiguration(name, factory);
    }

    private static ColumnGeneratorFactory zip() {
        return random -> new ZipCodeGenerator.Builder(random).build();
    }

    private static ColumnGeneratorFactory data() {
        return random -> new DataColumnGenerator.Builder(random).build();
    }

    private static ColumnGeneratorFactory lastName() {
        return random -> new CustomerLastNameGenerator.Builder(random).build();
    }

    private static ColumnGeneratorFactory credit() {
        return random -> new CreditGenerator.Builder(random).build();
    }

    private static ColumnGeneratorFactory staticString(String value) {
        return random -> new StaticStringGenerator.Builder(random).value(value).build();
    }

    private static ColumnGeneratorFactory staticInt(int value) {
        return random -> new StaticIntegerGenerator.Builder(random).value(value).build();
    }

    private static Set<ColumnConfiguration> set(ColumnConfiguration... configurations) {
        return new HashSet<>(Set.of(configurations));
    }
}
