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
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TPCCConfigurationTest {

    private static final int W = 2;
    private static final int I = 50;
    private static final int D = 3;
    private static final int C = 10;
    private static final int L = 5;
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
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, L, NEW_ORDERS);

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
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, L);
        assertEquals((long) W * D * C, table(tables, "new_order").rowCount());
    }

    @Test
    void taxRatesStayWithinSpecRange() {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, L, NEW_ORDERS);
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
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, L, NEW_ORDERS);
        assertEquals(new BigDecimal("300000.00"), generator(table(tables, "warehouse"), "w_ytd").generate());
        assertEquals(new BigDecimal("30000.00"), generator(table(tables, "district"), "d_ytd").generate());
        assertEquals(new BigDecimal("50000.00"), generator(table(tables, "customer"), "c_credit_lim").generate());
        assertEquals(new BigDecimal("-10.00"), generator(table(tables, "customer"), "c_balance").generate());
        assertEquals(5, ((Integer) generator(table(tables, "order_line"), "ol_quantity").generate()).intValue());
        // d_next_o_id is orders-per-district + 1
        assertEquals(C + 1, ((Integer) generator(table(tables, "district"), "d_next_o_id").generate()).intValue());
    }
}
