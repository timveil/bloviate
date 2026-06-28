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
 * Generates {@code float} values drawn uniformly from the half-open range
 * {@code [startInclusive, endExclusive)} using the seeded random source, so a given seed
 * yields a reproducible sequence. The default range is {@code [0, Float.MAX_VALUE)}. The
 * single-precision analogue of {@link DoubleGenerator}.
 */
public class FloatGenerator extends AbstractDataGenerator<Float> {

    private final float startInclusive;
    private final float endExclusive;

    @Override
    public Float generate() {
        return randomUtils.nextFloat(startInclusive, endExclusive);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Float value) throws SQLException {
        statement.setFloat(parameterIndex, value);
    }

    @Override
    public Float get(ResultSet resultSet, int columnIndex) throws SQLException {
        float value = resultSet.getFloat(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    public static class Builder extends AbstractBuilder<Float> {

        private float startInclusive = 0;
        private float endExclusive = Float.MAX_VALUE;

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
        public Builder start(float start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range. Defaults to {@code Float.MAX_VALUE};
         * generated values are always strictly less than this bound.
         *
         * @param end the upper bound that may never be generated (exclusive)
         * @return this builder, for chaining
         */
        public Builder end(float end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public FloatGenerator build() {
            return new FloatGenerator(this);
        }
    }

    private FloatGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
