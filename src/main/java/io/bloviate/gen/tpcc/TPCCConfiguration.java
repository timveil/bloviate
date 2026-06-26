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
import io.bloviate.gen.ChildCardinality;
import io.bloviate.gen.ChildCountGenerator;
import io.bloviate.gen.ChildKeyComponentGenerator;
import io.bloviate.gen.CompositeKeyComponentGenerator;
import io.bloviate.gen.GroupedPermutationGenerator;
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.ScaledBigDecimalGenerator;
import io.bloviate.gen.StaticBigDecimalGenerator;
import io.bloviate.gen.StaticFloatGenerator;
import io.bloviate.gen.StaticIntegerGenerator;
import io.bloviate.gen.StaticStringGenerator;
import io.bloviate.gen.VariableStringGenerator;

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
 * the TPC-C generators in this package; the remaining non-key columns are
 * configured with bounded generators matching the value ranges and seed values
 * from the TPC-C specification (clause 4.3.3.1).
 *
 * <p>In line with the spec, each order has a random {@code o_ol_cnt} number of
 * order lines in {@code [minLinesPerOrder, maxLinesPerOrder]} (default
 * {@code [5, 15]}) and {@code order_line} holds exactly that many rows per order
 * (see {@link io.bloviate.gen.ChildCardinality}); and {@code new_order} holds only the most
 * recent {@code newOrdersPerDistrict} orders per district (the highest
 * {@code o_id}s) rather than mirroring every order.
 *
 * <p>Two parts of the spec are still simplified relative to a strict benchmark
 * load:
 * <ul>
 *   <li>{@code o_c_id} is a per-district random permutation of customer ids (each
 *       customer has exactly one order), but {@code c_last} uses {@code NURand} for
 *       all rows rather than the spec's deterministic enumeration of the first 1000
 *       names per district;</li>
 *   <li>delivery state is not modelled: {@code o_carrier_id} is always populated
 *       and {@code ol_delivery_d} is left to the default generator rather than
 *       being NULL for the most recent (undelivered) orders.</li>
 * </ul>
 */
public final class TPCCConfiguration {

    public static final int DEFAULT_ITEMS = 100_000;
    public static final int DEFAULT_DISTRICTS_PER_WAREHOUSE = 10;
    public static final int DEFAULT_CUSTOMERS_PER_DISTRICT = 3_000;
    public static final int DEFAULT_MIN_LINES_PER_ORDER = 5;
    public static final int DEFAULT_MAX_LINES_PER_ORDER = 15;
    public static final int DEFAULT_NEW_ORDERS_PER_DISTRICT = 900;

    // salt for the deterministic per-order line-count hash; fixed so datasets are reproducible
    private static final long LINE_COUNT_SEED = 0x7C_3C_45_21_9ABCDEFL;

    // salt for the per-district o_c_id permutation; fixed so datasets are reproducible
    private static final long O_C_ID_PERMUTATION_SEED = 0x0C_1D_5EED_9ABCDEFL;

    private TPCCConfiguration() {
    }

    /**
     * Builds a TPC-C configuration using the default per-warehouse cardinalities.
     *
     * @param warehouses the number of warehouses (the TPC-C scale factor)
     * @return the set of table configurations
     */
    public static Set<TableConfiguration> build(int warehouses) {
        return build(warehouses, DEFAULT_ITEMS, DEFAULT_DISTRICTS_PER_WAREHOUSE, DEFAULT_CUSTOMERS_PER_DISTRICT,
                DEFAULT_MIN_LINES_PER_ORDER, DEFAULT_MAX_LINES_PER_ORDER);
    }

    /**
     * Builds a TPC-C configuration with explicit cardinalities (useful for tests).
     * The number of {@code new_order} rows per district defaults to
     * {@link #DEFAULT_NEW_ORDERS_PER_DISTRICT}, clamped to the orders-per-district.
     *
     * @param warehouses           number of warehouses (W)
     * @param items                number of items (I)
     * @param districtsPerWarehouse districts per warehouse (D)
     * @param customersPerDistrict  customers per district (C); also used as orders per district
     * @param minLinesPerOrder      minimum order lines per order (inclusive)
     * @param maxLinesPerOrder      maximum order lines per order (inclusive); pass the same value
     *                              as {@code minLinesPerOrder} for a fixed line count
     * @return the set of table configurations
     */
    public static Set<TableConfiguration> build(int warehouses, int items, int districtsPerWarehouse, int customersPerDistrict, int minLinesPerOrder, int maxLinesPerOrder) {
        return build(warehouses, items, districtsPerWarehouse, customersPerDistrict, minLinesPerOrder, maxLinesPerOrder,
                Math.min(customersPerDistrict, DEFAULT_NEW_ORDERS_PER_DISTRICT));
    }

    /**
     * Builds a TPC-C configuration with explicit cardinalities, including the
     * number of {@code new_order} rows per district.
     *
     * @param warehouses             number of warehouses (W)
     * @param items                  number of items (I)
     * @param districtsPerWarehouse  districts per warehouse (D)
     * @param customersPerDistrict   customers per district (C); also used as orders per district
     * @param minLinesPerOrder       minimum order lines per order (inclusive)
     * @param maxLinesPerOrder       maximum order lines per order (inclusive); pass the same value
     *                               as {@code minLinesPerOrder} for a fixed line count
     * @param newOrdersPerDistrict   most-recent orders per district mirrored into {@code new_order};
     *                               clamped to {@code [0, customersPerDistrict]}
     * @return the set of table configurations
     */
    public static Set<TableConfiguration> build(int warehouses, int items, int districtsPerWarehouse, int customersPerDistrict, int minLinesPerOrder, int maxLinesPerOrder, int newOrdersPerDistrict) {

        final int w = warehouses;
        final int i = items;
        final int d = districtsPerWarehouse;
        final int c = customersPerDistrict;
        final int o = customersPerDistrict; // one order per customer
        final int no = Math.max(0, Math.min(newOrdersPerDistrict, o)); // new_order is a subset of orders

        // shared, deterministic per-order line counts so open_order.o_ol_cnt and order_line agree
        final ChildCardinality cardinality = new ChildCardinality(minLinesPerOrder, maxLinesPerOrder, LINE_COUNT_SEED);

        final long stockRows = (long) w * i;
        final long districtRows = (long) w * d;
        final long customerRows = (long) w * d * c;
        final long orderRows = (long) w * d * o;
        final long orderLineRows = cardinality.total(orderRows);
        final long newOrderRows = (long) w * d * no;

        Set<TableConfiguration> tables = new HashSet<>();

        tables.add(new TableConfiguration("warehouse", w, set(
                key("w_id", 1, 1, w),
                col("w_zip", zip()),
                col("w_tax", taxRate()),
                col("w_ytd", staticDecimal("300000.00")))));

        tables.add(new TableConfiguration("item", i, set(
                key("i_id", 1, 1, i),
                col("i_data", data()),
                col("i_im_id", intRange(1, 10_000)),
                col("i_price", scaledDecimal(1.0, 100.0, 2)))));

        tables.add(new TableConfiguration("stock", stockRows, set(
                key("s_w_id", 1, i, w),
                key("s_i_id", 1, 1, i),
                col("s_data", data()),
                col("s_quantity", intRange(10, 100)),
                col("s_ytd", staticDecimal("0.00")),
                col("s_order_cnt", staticInt(0)),
                col("s_remote_cnt", staticInt(0)))));

        tables.add(new TableConfiguration("district", districtRows, set(
                key("d_w_id", 1, d, w),
                key("d_id", 1, 1, d),
                col("d_zip", zip()),
                col("d_tax", taxRate()),
                col("d_ytd", staticDecimal("30000.00")),
                col("d_next_o_id", staticInt(o + 1)))));

        tables.add(new TableConfiguration("customer", customerRows, set(
                key("c_w_id", 1, (long) d * c, w),
                key("c_d_id", 1, c, d),
                key("c_id", 1, 1, c),
                col("c_zip", zip()),
                col("c_last", lastName()),
                col("c_credit", credit()),
                col("c_middle", staticString("OE")),
                col("c_discount", scaledDecimal(0.0, 0.5, 4)),
                col("c_credit_lim", staticDecimal("50000.00")),
                col("c_balance", staticDecimal("-10.00")),
                col("c_ytd_payment", staticFloat(10.0f)),
                col("c_payment_cnt", staticInt(1)),
                col("c_delivery_cnt", staticInt(0)),
                col("c_phone", numericString(16)),
                col("c_data", variableString(300, 500)))));

        tables.add(new TableConfiguration("history", customerRows, set(
                key("h_c_w_id", 1, (long) d * c, w),
                key("h_c_d_id", 1, c, d),
                key("h_c_id", 1, 1, c),
                key("h_w_id", 1, (long) d * c, w),
                key("h_d_id", 1, c, d),
                col("h_amount", staticDecimal("10.00")),
                col("h_data", variableString(12, 24)))));

        tables.add(new TableConfiguration("open_order", orderRows, set(
                key("o_w_id", 1, (long) d * o, w),
                key("o_d_id", 1, o, d),
                key("o_id", 1, 1, o),
                permutation("o_c_id", c, 1),
                col("o_carrier_id", intRange(1, 10)),
                childCount("o_ol_cnt", cardinality),
                col("o_all_local", staticInt(1)))));

        tables.add(new TableConfiguration("new_order", newOrderRows, set(
                key("no_w_id", 1, (long) d * no, w),
                key("no_d_id", 1, no, d),
                key("no_o_id", o - no + 1, 1, no))));

        // order_line has a variable number of rows per order; its parent-key components mirror
        // the open_order key generators (same start/repeat/cycle, applied to the parent index),
        // and ol_number is the 1-based line sequence within each order
        tables.add(new TableConfiguration("order_line", orderLineRows, set(
                childKey("ol_w_id", cardinality, 1, (long) d * o, w),
                childKey("ol_d_id", cardinality, 1, o, d),
                childKey("ol_o_id", cardinality, 1, 1, o),
                childSequence("ol_number", cardinality),
                childKey("ol_supply_w_id", cardinality, 1, (long) d * o, w),
                key("ol_i_id", 1, 1, i),
                col("ol_quantity", staticInt(5)),
                col("ol_amount", scaledDecimal(0.01, 9999.99, 2)))));

        return tables;
    }

    /**
     * A parent table's "number of children" column, driven by the shared {@code cardinality}.
     */
    private static ColumnConfiguration childCount(String name, ChildCardinality cardinality) {
        return new ColumnConfiguration(name,
                random -> new ChildCountGenerator.Builder(random).cardinality(cardinality).build());
    }

    /**
     * A child-key component that repeats a parent-key dimension, computed from the parent index
     * with the same {@code start + ((parentIndex / repeat) % cycle)} formula as the parent's key.
     */
    private static ColumnConfiguration childKey(String name, ChildCardinality cardinality, int start, long repeat, int cycle) {
        return new ColumnConfiguration(name,
                random -> new ChildKeyComponentGenerator.Builder(random).cardinality(cardinality).start(start).repeat(repeat).cycle(cycle).build());
    }

    /**
     * A child-key component emitting the 1-based sequence number of a child within its parent.
     */
    private static ColumnConfiguration childSequence(String name, ChildCardinality cardinality) {
        return new ColumnConfiguration(name,
                random -> new ChildKeyComponentGenerator.Builder(random).cardinality(cardinality).sequence().start(1).build());
    }

    /**
     * A column that is a random permutation of {@code [start, start + groupSize)} within each
     * consecutive run of {@code groupSize} rows (e.g. {@code o_c_id} per district).
     */
    private static ColumnConfiguration permutation(String name, int groupSize, int start) {
        return new ColumnConfiguration(name,
                random -> new GroupedPermutationGenerator.Builder(random).groupSize(groupSize).start(start).seed(O_C_ID_PERMUTATION_SEED).build());
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

    private static ColumnGeneratorFactory staticFloat(float value) {
        return random -> new StaticFloatGenerator.Builder(random).value(value).build();
    }

    private static ColumnGeneratorFactory staticDecimal(String value) {
        return random -> new StaticBigDecimalGenerator.Builder(random).value(value).build();
    }

    /**
     * A random decimal drawn uniformly from {@code [start, end)} rounded to {@code scale} places.
     */
    private static ColumnGeneratorFactory scaledDecimal(double start, double end, int scale) {
        return random -> new ScaledBigDecimalGenerator.Builder(random).start(start).end(end).scale(scale).build();
    }

    /**
     * Tax rate per the spec: a decimal in {@code [0.0000, 0.2000]} with scale 4.
     */
    private static ColumnGeneratorFactory taxRate() {
        return scaledDecimal(0.0, 0.2, 4);
    }

    /**
     * A random integer in the inclusive range {@code [startInclusive, endInclusive]}.
     */
    private static ColumnGeneratorFactory intRange(int startInclusive, int endInclusive) {
        return random -> new IntegerGenerator.Builder(random).start(startInclusive).end(endInclusive + 1).build();
    }

    private static ColumnGeneratorFactory variableString(int minLength, int maxLength) {
        return random -> new VariableStringGenerator.Builder(random).minLength(minLength).maxLength(maxLength).letters(true).numbers(true).build();
    }

    private static ColumnGeneratorFactory numericString(int length) {
        return random -> new VariableStringGenerator.Builder(random).minLength(length).maxLength(length).letters(false).numbers(true).build();
    }

    private static Set<ColumnConfiguration> set(ColumnConfiguration... configurations) {
        return new HashSet<>(Set.of(configurations));
    }
}
