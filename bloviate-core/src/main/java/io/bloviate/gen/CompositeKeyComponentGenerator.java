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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates one component (dimension) of a row-ordered cartesian product, which
 * is what is needed to populate composite primary/foreign keys densely and
 * without collisions.
 *
 * <p>For the n-th row (0-based) the value is
 * {@code start + ((n / repeat) % cycle)}, where:
 * <ul>
 *   <li>{@code cycle} is the size of this dimension, and</li>
 *   <li>{@code repeat} is how many consecutive rows share a value — i.e. the
 *       product of the sizes of all dimensions nested inside this one.</li>
 * </ul>
 *
 * <p>Example — a composite key over warehouses (W) × items (I), filled in
 * row-major order with warehouse outermost: configure the warehouse component
 * with {@code repeat=I, cycle=W} and the item component with
 * {@code repeat=1, cycle=I}. Filling exactly {@code W * I} rows then yields every
 * {@code (w, i)} pair exactly once.
 *
 * <p>The produced values do not depend on the random source; counter state is
 * held by the generator instance and advances on every {@link #generate()}.
 */
public class CompositeKeyComponentGenerator extends AbstractDataGenerator<Integer> {

    private final int start;
    private final long repeat;
    private final int cycle;
    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public Integer generate() {
        long n = counter.getAndIncrement();
        return start + (int) ((n / repeat) % cycle);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        statement.setInt(parameterIndex, value);
    }

    @Override
    public Integer get(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    public static class Builder extends AbstractBuilder<Integer> {

        private int start = 1;
        private long repeat = 1;
        private int cycle = 1;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder start(int start) {
            this.start = start;
            return this;
        }

        public Builder repeat(long repeat) {
            this.repeat = repeat;
            return this;
        }

        public Builder cycle(int cycle) {
            this.cycle = cycle;
            return this;
        }

        @Override
        public CompositeKeyComponentGenerator build() {
            return new CompositeKeyComponentGenerator(this);
        }
    }

    private CompositeKeyComponentGenerator(Builder builder) {
        super(builder.random);
        this.start = builder.start;
        this.repeat = builder.repeat;
        this.cycle = builder.cycle;
    }
}
