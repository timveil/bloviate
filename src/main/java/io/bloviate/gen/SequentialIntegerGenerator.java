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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a monotonically increasing sequence of integers over the inclusive
 * range {@code [startInclusive, endInclusive]}, wrapping back to the start after
 * the end is emitted. Useful for surrogate keys that must be dense and unique.
 *
 * <p>Unlike most generators the produced values do not depend on the random
 * source; the counter state is held by the generator instance and advances on
 * every call to {@link #generate()}.
 */
public class SequentialIntegerGenerator extends AbstractDataGenerator<Integer> {

    private final int startInclusive;
    private final int endInclusive;
    private final AtomicInteger counter;

    @Override
    public Integer generate() {
        return counter.getAndUpdate(current -> current >= endInclusive ? startInclusive : current + 1);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        statement.setInt(parameterIndex, value);
    }

    @Override
    public Integer get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getInt(columnIndex);
    }

    public static class Builder extends AbstractBuilder<Integer> {

        private int startInclusive = 1;
        private int endInclusive = Integer.MAX_VALUE;

        public Builder(Random random) {
            super(random);
        }

        public Builder start(int start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(int end) {
            this.endInclusive = end;
            return this;
        }

        @Override
        public SequentialIntegerGenerator build() {
            if (endInclusive < startInclusive) {
                throw new IllegalArgumentException(
                        "end (" + endInclusive + ") must be >= start (" + startInclusive + ")");
            }
            return new SequentialIntegerGenerator(this);
        }
    }

    private SequentialIntegerGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endInclusive = builder.endInclusive;
        this.counter = new AtomicInteger(builder.startInclusive);
    }
}
