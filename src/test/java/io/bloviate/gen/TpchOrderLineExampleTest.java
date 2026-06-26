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

package io.bloviate.gen;

import io.bloviate.db.ColumnConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worked example: applying the generic variable parent-child cardinality generators
 * ({@link ChildCardinality}, {@link ChildKeyComponentGenerator}, {@link ChildCountGenerator})
 * to a schema <em>other than</em> TPC-C — the TPC-H {@code ORDERS -> LINEITEM} relationship,
 * where each order has a random number of line items (the spec draws this from {@code [1, 7]}).
 *
 * <p>This is deliberately simpler than TPC-C: a TPC-H order is identified by a single
 * {@code o_orderkey}, so the child's parent-key component is just one dimension rather than a
 * three-part composite. To populate {@code LINEITEM} you register, on its
 * {@code TableConfiguration}, the column overrides built in {@link #lineitemColumns} and set the
 * table's row count to {@code lineitemsPerOrder.total(orderCount)}. The matching
 * {@code ChildCardinality} instance is shared so the parent and child agree on counts:
 *
 * <pre>{@code
 * ChildCardinality lineitemsPerOrder = new ChildCardinality(1, 7, seed);
 * long orderCount   = scaleFactor * 1_500_000L;       // TPC-H ORDERS cardinality
 * long lineitemRows = lineitemsPerOrder.total(orderCount);
 *
 * // LINEITEM.l_orderkey — the parent's single-column key, applied to the parent index
 * new ColumnConfiguration("l_orderkey", r -> new ChildKeyComponentGenerator.Builder(r)
 *         .cardinality(lineitemsPerOrder).start(1).repeat(1).cycle((int) orderCount).build());
 * // LINEITEM.l_linenumber — 1-based line sequence within each order
 * new ColumnConfiguration("l_linenumber", r -> new ChildKeyComponentGenerator.Builder(r)
 *         .cardinality(lineitemsPerOrder).sequence().start(1).build());
 * }</pre>
 *
 * <p>TPC-H {@code ORDERS} has no stored line-count column, so {@link ChildCountGenerator} is
 * optional here — it is shown only to illustrate that, were you to add a derived count column,
 * sharing the same {@code ChildCardinality} keeps it consistent with the generated line items.
 */
class TpchOrderLineExampleTest {

    // a small order count for the example; real TPC-H uses scaleFactor * 1,500,000 orders
    private static final long ORDER_COUNT = 1_000;
    private static final int MIN_LINES = 1;
    private static final int MAX_LINES = 7;
    private static final long SEED = 0x7C_3C_45_21_9ABCDEFL;

    /**
     * The {@code LINEITEM} key column overrides you would register on its {@code TableConfiguration}.
     */
    private static List<ColumnConfiguration> lineitemColumns(ChildCardinality lineitemsPerOrder, long orderCount) {
        return List.of(
                new ColumnConfiguration("l_orderkey", random -> new ChildKeyComponentGenerator.Builder(random)
                        .cardinality(lineitemsPerOrder).start(1).repeat(1).cycle((int) orderCount).build()),
                new ColumnConfiguration("l_linenumber", random -> new ChildKeyComponentGenerator.Builder(random)
                        .cardinality(lineitemsPerOrder).sequence().start(1).build()));
    }

    @Test
    void lineitemRowsAgreeWithOrders() {
        ChildCardinality lineitemsPerOrder = new ChildCardinality(MIN_LINES, MAX_LINES, SEED);

        // LINEITEM row count = sum over all orders of lines(order)
        long lineitemRows = lineitemsPerOrder.total(ORDER_COUNT);

        // build the generators exactly as the fill engine would, from the column overrides
        List<ColumnConfiguration> columns = lineitemColumns(lineitemsPerOrder, ORDER_COUNT);
        DataGenerator<?> lOrderKey = columns.get(0).generatorFactory().create(new Random());
        DataGenerator<?> lLineNumber = columns.get(1).generatorFactory().create(new Random());

        // optional derived ORDERS count column, shown for completeness
        ChildCountGenerator oLineCount = new ChildCountGenerator.Builder(new Random())
                .cardinality(lineitemsPerOrder).build();

        // walk every LINEITEM row and group its line numbers by order key
        Map<Integer, List<Integer>> linesByOrder = new HashMap<>();
        for (long row = 0; row < lineitemRows; row++) {
            int orderKey = (Integer) lOrderKey.generate();
            int lineNumber = (Integer) lLineNumber.generate();
            assertTrue(orderKey >= 1 && orderKey <= ORDER_COUNT, "l_orderkey out of range: " + orderKey);
            linesByOrder.computeIfAbsent(orderKey, k -> new ArrayList<>()).add(lineNumber);
        }

        // every order has exactly count(order) line items, numbered 1..count, and the totals agree
        long summed = 0;
        for (long k = 0; k < ORDER_COUNT; k++) {
            int orderKey = (int) (k + 1);
            int expected = lineitemsPerOrder.count(k);
            assertEquals(expected, oLineCount.generate().intValue(), "o_lineitem_cnt mismatch for order " + orderKey);

            List<Integer> lines = linesByOrder.get(orderKey);
            assertNotNull(lines, "order " + orderKey + " has no line items");
            assertEquals(expected, lines.size(), "line count mismatch for order " + orderKey);
            for (int line = 0; line < expected; line++) {
                assertEquals(line + 1, lines.get(line).intValue(), "non-contiguous l_linenumber for order " + orderKey);
            }
            summed += expected;
        }

        assertEquals(summed, lineitemRows, "LINEITEM row count must equal the sum of per-order line counts");
        assertEquals(ORDER_COUNT, linesByOrder.size(), "every order should have at least one line item (min = 1)");
    }
}
