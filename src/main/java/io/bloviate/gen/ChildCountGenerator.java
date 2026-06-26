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
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits a parent table's "number of children" column under variable parent-child
 * cardinality: for the k-th row (0-based) it returns
 * {@link ChildCardinality#count(long) cardinality.count(k)} (for example TPC-C's
 * {@code o_ol_cnt}). Sharing the same {@link ChildCardinality} instance with the child
 * table's {@link ChildKeyComponentGenerator}s guarantees that each parent's declared
 * child count equals the number of child rows generated for it.
 *
 * <p>The produced values do not depend on the random source; counter state is held by
 * the generator instance and advances on every {@link #generate()}.
 */
public class ChildCountGenerator extends AbstractDataGenerator<Integer> {

    private final ChildCardinality cardinality;
    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public Integer generate() {
        return cardinality.count(counter.getAndIncrement());
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

        private ChildCardinality cardinality;

        public Builder(Random random) {
            super(random);
        }

        public Builder cardinality(ChildCardinality cardinality) {
            this.cardinality = cardinality;
            return this;
        }

        @Override
        public ChildCountGenerator build() {
            return new ChildCountGenerator(this);
        }
    }

    private ChildCountGenerator(Builder builder) {
        super(builder.random);
        if (builder.cardinality == null) {
            throw new IllegalStateException("cardinality is required");
        }
        this.cardinality = builder.cardinality;
    }
}
