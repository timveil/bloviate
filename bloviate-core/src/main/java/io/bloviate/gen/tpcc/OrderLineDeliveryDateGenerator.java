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

import io.bloviate.gen.AbstractBuilder;
import io.bloviate.gen.AbstractDataGenerator;
import io.bloviate.gen.ChildCardinality;
import io.bloviate.gen.ChildKeyComponentGenerator;
import io.bloviate.gen.IndexedDataGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.random.RandomGenerator;

/**
 * Generates {@code ol_delivery_d} for the {@code order_line} table under variable order-line
 * cardinality: a delivery timestamp for the lines of <em>delivered</em> orders, and {@code NULL}
 * for the lines of the most recent (undelivered) orders.
 *
 * <p>An order is delivered when its {@code o_id} is at or below {@code deliveredThreshold}
 * (the lower {@code o_id}s of each district; the higher ones are the undelivered {@code new_order}
 * subset). Because {@code order_line} has a variable number of rows per order, this generator reuses
 * a {@link ChildKeyComponentGenerator} configured to emit each line's parent {@code o_id} as an
 * independent lockstep walker over the shared {@link ChildCardinality} — so it needs no walk state
 * of its own and stays consistent with the {@code order_line} key columns.
 *
 * <p>The delivery timestamp approximates the spec's {@code ol_delivery_d = o_entry_d}; the
 * fidelity that matters is that undelivered orders' lines are NULL.
 */
public class OrderLineDeliveryDateGenerator extends AbstractDataGenerator<Timestamp> implements IndexedDataGenerator {

    private final ChildKeyComponentGenerator orderId;
    private final int deliveredThreshold;

    @Override
    public Timestamp generate() {
        int oId = orderId.generate();
        return oId <= deliveredThreshold ? Timestamp.from(Instant.now()) : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The delivered/undelivered (timestamp-vs-NULL) decision is positional — it comes entirely
     * from the wrapped {@link ChildKeyComponentGenerator} walker — so seeking delegates to it,
     * keeping the column aligned with the {@code order_line} keys under intra-table partitioning.
     */
    @Override
    public void seek(long rowIndex) {
        orderId.seek(rowIndex);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Timestamp value) throws SQLException {
        statement.setTimestamp(parameterIndex, value);
    }

    @Override
    public Timestamp get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTimestamp(columnIndex);
    }

    public static class Builder extends AbstractBuilder<Timestamp> {

        private ChildCardinality cardinality;
        private int ordersPerDistrict = 1;
        private int deliveredThreshold = 0;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder cardinality(ChildCardinality cardinality) {
            this.cardinality = cardinality;
            return this;
        }

        public Builder ordersPerDistrict(int ordersPerDistrict) {
            this.ordersPerDistrict = ordersPerDistrict;
            return this;
        }

        /**
         * Orders with {@code o_id <= deliveredThreshold} are delivered (timestamp); the rest are
         * undelivered (NULL). For TPC-C this is {@code ordersPerDistrict - newOrdersPerDistrict}.
         */
        public Builder deliveredThreshold(int deliveredThreshold) {
            this.deliveredThreshold = deliveredThreshold;
            return this;
        }

        @Override
        public OrderLineDeliveryDateGenerator build() {
            return new OrderLineDeliveryDateGenerator(random, this);
        }
    }

    private OrderLineDeliveryDateGenerator(RandomGenerator random, Builder builder) {
        super(random);
        if (builder.cardinality == null) {
            throw new IllegalStateException("cardinality is required");
        }
        // an independent lockstep walker that yields each line's parent o_id (1 + parentIndex % O)
        this.orderId = new ChildKeyComponentGenerator.Builder(random)
                .cardinality(builder.cardinality).start(1).repeat(1).cycle(builder.ordersPerDistrict).build();
        this.deliveredThreshold = builder.deliveredThreshold;
    }
}
