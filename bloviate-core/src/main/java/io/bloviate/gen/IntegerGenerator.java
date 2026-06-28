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
 * Generates {@code int} values drawn uniformly from the half-open range
 * {@code [startInclusive, endExclusive)} using the seeded random source, so a given seed
 * yields a reproducible sequence. The default range is {@code [0, Integer.MAX_VALUE)}.
 * For non-uniform whole-number distributions see {@link NormalIntegerGenerator} (Gaussian)
 * and {@link ZipfianIntegerGenerator} (skewed), or {@link StaticIntegerGenerator} for a fixed value.
 */
public class IntegerGenerator extends AbstractDataGenerator<Integer> {

    private final int startInclusive;
    private final int endExclusive;

    @Override
    public Integer generate() {
        return randomUtils.nextInt(startInclusive, endExclusive);
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

    /** Fluent builder for {@link IntegerGenerator}. */
    public static class Builder extends AbstractBuilder<Integer> {

        private int startInclusive = 0;
        private int endExclusive = Integer.MAX_VALUE;

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
        public Builder start(int start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range. Defaults to {@code Integer.MAX_VALUE};
         * generated values are always strictly less than this bound.
         *
         * @param end the upper bound that may never be generated (exclusive)
         * @return this builder, for chaining
         */
        public Builder end(int end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public IntegerGenerator build() {
            return new IntegerGenerator(this);
        }
    }

    private IntegerGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
