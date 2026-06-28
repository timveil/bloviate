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

/**
 * Generates {@code long} values drawn uniformly from the half-open range
 * {@code [startInclusive, endExclusive)} using the seeded random source, so a given seed
 * yields a reproducible sequence. The default range is {@code [0, Long.MAX_VALUE)}. The
 * 64-bit analogue of {@link IntegerGenerator}.
 */
public class LongGenerator extends AbstractDataGenerator<Long> {

    private final long startInclusive;
    private final long endExclusive;

    @Override
    public Long generate() {
        return randomUtils.nextLong(startInclusive, endExclusive);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Long value) throws SQLException {
        statement.setLong(parameterIndex, value);
    }

    @Override
    public Long get(ResultSet resultSet, int columnIndex) throws SQLException {
        long value = resultSet.getLong(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    public static class Builder extends AbstractBuilder<Long> {

        private long startInclusive = 0;
        private long endExclusive = Long.MAX_VALUE;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the inclusive lower bound of the generated range. Defaults to {@code 0}.
         *
         * @param start the smallest value that may be generated (inclusive)
         * @return this builder, for chaining
         */
        public Builder start(long start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range. Defaults to {@code Long.MAX_VALUE};
         * generated values are always strictly less than this bound.
         *
         * @param end the upper bound that may never be generated (exclusive)
         * @return this builder, for chaining
         */
        public Builder end(long end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public LongGenerator build() {
            return new LongGenerator(this);
        }
    }

    private LongGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
