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
 * Generates {@code short} values drawn uniformly from the half-open range
 * {@code [startInclusive, endExclusive)} using the seeded random source, so a given seed
 * yields a reproducible sequence. Delegates to an {@link IntegerGenerator} and narrows the
 * result to a {@code short}; the bounds are therefore constrained to
 * {@code [Short.MIN_VALUE, Short.MAX_VALUE]}. The default range is {@code [0, Short.MAX_VALUE)}.
 */
public class ShortGenerator extends AbstractDataGenerator<Short> {

    private final IntegerGenerator integerGenerator;

    @Override
    public Short generate() {
        return integerGenerator.generate().shortValue();
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Short value) throws SQLException {
        statement.setShort(parameterIndex, value);
    }

    @Override
    public Short get(ResultSet resultSet, int columnIndex) throws SQLException {
        short value = resultSet.getShort(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    /** Fluent builder for {@link ShortGenerator}. */
    public static class Builder extends AbstractBuilder<Short> {

        private int startInclusive = 0;
        private int endExclusive = Short.MAX_VALUE;

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
         * @throws IllegalArgumentException if {@code start} is less than {@code Short.MIN_VALUE}
         */
        public Builder start(int start) {
            if (start < Short.MIN_VALUE) {
                throw new IllegalArgumentException("invalid start value.  Less than Short.MIN_VALUE.");
            }
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range. Defaults to {@code Short.MAX_VALUE};
         * generated values are always strictly less than this bound.
         *
         * @param end the upper bound that may never be generated (exclusive)
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code end} is greater than {@code Short.MAX_VALUE}
         */
        public Builder end(int end) {
            if (end > Short.MAX_VALUE) {
                throw new IllegalArgumentException("invalid end value.  Greater than Short.MAX_VALUE.");
            }

            this.endExclusive = end;
            return this;
        }

        @Override
        public ShortGenerator build() {
            return new ShortGenerator(this);
        }
    }

    private ShortGenerator(Builder builder) {
        super(builder.random);
        this.integerGenerator = new IntegerGenerator.Builder(builder.random).start(builder.startInclusive).end(builder.endExclusive).build();
    }
}
