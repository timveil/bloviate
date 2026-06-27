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
import io.bloviate.db.TableConfiguration;
import io.bloviate.gen.DataGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TPCCConfigurationTest {

    private static final int W = 2;
    private static final int I = 50;
    private static final int D = 3;
    private static final int C = 10;
    private static final int MIN_LINES = 5;
    private static final int MAX_LINES = 15;
    private static final int NEW_ORDERS = 4;

    private static TableConfiguration table(Set<TableConfiguration> tables, String name) {
        return tables.stream().filter(t -> t.tableName().equals(name)).findFirst().orElseThrow();
    }

    private static DataGenerator<?> generator(TableConfiguration table, String column) {
        ColumnConfiguration config = table.columnConfiguration(column);
        assertNotNull(config, "expected an override for column " + column);
        return config.generatorFactory().create(new Random(1));
    }

    @Test
    void newOrderIsASubsetOfOrders() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);

        long expectedOrders = (long) W * D * C;
        long expectedNewOrders = (long) W * D * NEW_ORDERS;

        assertEquals(expectedOrders, table(tables, "open_order").rowCount());
        assertEquals(expectedNewOrders, table(tables, "new_order").rowCount());

        // no_o_id must reference only the most-recent orders: [C - NEW_ORDERS + 1, C]
        DataGenerator<?> noOId = generator(table(tables, "new_order"), "no_o_id");
        int min = C - NEW_ORDERS + 1;
        for (long i = 0; i < expectedNewOrders; i++) {
            int value = (Integer) noOId.generate();
            assertTrue(value >= min && value <= C, "no_o_id out of subset range: " + value);
        }
    }

    @Test
    void newOrdersPerDistrictDefaultsToClampedSpecValue() {
        // small order count clamps the 900-default down to the available orders (full mirror)
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES);
        assertEquals((long) W * D * C, table(tables, "new_order").rowCount());
    }

    @Test
    void taxRatesStayWithinSpecRange() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);
        DataGenerator<?> wTax = generator(table(tables, "warehouse"), "w_tax");
        BigDecimal max = new BigDecimal("0.2000");
        for (int i = 0; i < 1000; i++) {
            BigDecimal value = (BigDecimal) wTax.generate();
            assertEquals(4, value.scale());
            assertTrue(value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(max) <= 0, "w_tax out of range: " + value);
        }
    }

    @Test
    void seedValuesMatchSpec() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);
        assertEquals(new BigDecimal("300000.00"), generator(table(tables, "warehouse"), "w_ytd").generate());
        assertEquals(new BigDecimal("30000.00"), generator(table(tables, "district"), "d_ytd").generate());
        assertEquals(new BigDecimal("50000.00"), generator(table(tables, "customer"), "c_credit_lim").generate());
        assertEquals(new BigDecimal("-10.00"), generator(table(tables, "customer"), "c_balance").generate());
        assertEquals(5, ((Integer) generator(table(tables, "order_line"), "ol_quantity").generate()).intValue());
        // d_next_o_id is orders-per-district + 1
        assertEquals(C + 1, ((Integer) generator(table(tables, "district"), "d_next_o_id").generate()).intValue());
    }

    @Test
    void orderLineRowCountEqualsSumOfVariableLineCounts() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);

        long orders = (long) W * D * C;
        DataGenerator<?> oOlCnt = generator(table(tables, "open_order"), "o_ol_cnt");

        long sum = 0;
        boolean sawVariation = false;
        int first = (Integer) oOlCnt.generate();
        sum += first;
        for (long k = 1; k < orders; k++) {
            int count = (Integer) oOlCnt.generate();
            assertTrue(count >= MIN_LINES && count <= MAX_LINES, "o_ol_cnt out of range: " + count);
            sawVariation |= (count != first);
            sum += count;
        }

        assertTrue(sawVariation, "expected variable line counts across orders");
        assertEquals(sum, table(tables, "order_line").rowCount(),
                "order_line row count must equal the sum of o_ol_cnt across all orders");
    }

    @Test
    void fixedLineCountGivesClosedFormOrderLineRowCount() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, 7, 7, NEW_ORDERS);
        assertEquals((long) W * D * C * 7, table(tables, "order_line").rowCount());
    }

    @Test
    void orderCustomerIdIsAPerDistrictPermutation() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);
        DataGenerator<?> oCId = generator(table(tables, "open_order"), "o_c_id");

        long districts = (long) W * D;
        boolean sawShuffle = false;
        for (long dist = 0; dist < districts; dist++) {
            Set<Integer> seen = new HashSet<>();
            for (int position = 0; position < C; position++) {
                int value = (Integer) oCId.generate();
                assertTrue(value >= 1 && value <= C, "o_c_id out of range: " + value);
                assertTrue(seen.add(value), "duplicate o_c_id within a district: " + value);
                sawShuffle |= (value != position + 1);
            }
            assertEquals(C, seen.size(), "o_c_id is not a full permutation of customers in a district");
        }
        assertTrue(sawShuffle, "expected o_c_id to be shuffled, not the identity");
    }

    @Test
    void deliveryStateLeavesTheMostRecentOrdersUndelivered() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);
        int delivered = C - NEW_ORDERS; // orders with o_id <= delivered are delivered

        // o_carrier_id: delivered orders carry [1,10]; the rest (undelivered) are NULL
        DataGenerator<?> carrier = generator(table(tables, "open_order"), "o_carrier_id");
        for (long n = 0; n < (long) W * D * C; n++) {
            int oId = 1 + (int) (n % C);
            Object value = carrier.generate();
            if (oId <= delivered) {
                assertNotNull(value, "delivered order missing carrier");
                int carrierId = (Integer) value;
                assertTrue(carrierId >= 1 && carrierId <= 10, "carrier out of range: " + carrierId);
            } else {
                assertNull(value, "undelivered order should have a null carrier");
            }
        }

        // ol_delivery_d: lines of delivered orders have a timestamp; lines of undelivered orders are NULL.
        // ol_o_id and ol_delivery_d are independent lockstep walkers, so they agree row-for-row.
        DataGenerator<?> olOId = generator(table(tables, "order_line"), "ol_o_id");
        DataGenerator<?> deliveryDate = generator(table(tables, "order_line"), "ol_delivery_d");
        long lines = table(tables, "order_line").rowCount();
        for (long n = 0; n < lines; n++) {
            int oId = (Integer) olOId.generate();
            Object value = deliveryDate.generate();
            if (oId <= delivered) {
                assertNotNull(value, "delivered order line missing delivery date");
            } else {
                assertNull(value, "undelivered order line should have a null delivery date");
            }
        }
    }
}
