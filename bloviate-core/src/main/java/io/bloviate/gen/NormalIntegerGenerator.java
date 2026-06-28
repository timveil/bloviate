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
 * Generates {@code int} values from a <em>normal (Gaussian)</em> distribution with the configured
 * mean and standard deviation, rounded to the nearest integer and clamped to the inclusive range
 * {@code [min, max]}. The integer analogue of {@link NormalDoubleGenerator} — for counts, ages,
 * ratings, and other whole-number columns that should cluster around a center.
 *
 * @since 2.11.0
 */
public class NormalIntegerGenerator extends AbstractDataGenerator<Integer> {

    private final double mean;
    private final double standardDeviation;
    private final int min;
    private final int max;

    @Override
    public Integer generate() {
        long rounded = Math.round(random.nextGaussian(mean, standardDeviation));
        return (int) Math.min(max, Math.max(min, rounded));
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

    /** Builds a {@link NormalIntegerGenerator}, defaulting to a standard normal distribution (mean 0, standard deviation 1) spanning the full {@code int} range. */
    public static class Builder extends AbstractBuilder<Integer> {

        private double mean = 0.0;
        private double standardDeviation = 1.0;
        private int min = Integer.MIN_VALUE;
        private int max = Integer.MAX_VALUE;

        /**
         * Creates a builder drawing from the given random source.
         *
         * @param random the random source backing the generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the distribution mean (center); kept as a {@code double} so the center may sit between integers. Returns this builder for chaining.
         *
         * @param mean the mean of the normal distribution
         * @return this builder
         */
        public Builder mean(double mean) {
            this.mean = mean;
            return this;
        }

        /**
         * Sets the distribution standard deviation (spread); must be non-negative. Returns this builder for chaining.
         *
         * @param standardDeviation the standard deviation of the normal distribution
         * @return this builder
         */
        public Builder standardDeviation(double standardDeviation) {
            this.standardDeviation = standardDeviation;
            return this;
        }

        /**
         * Sets the inclusive lower bound that rounded values are clamped to. Returns this builder for chaining.
         *
         * @param min the minimum value
         * @return this builder
         */
        public Builder min(int min) {
            this.min = min;
            return this;
        }

        /**
         * Sets the inclusive upper bound that rounded values are clamped to. Returns this builder for chaining.
         *
         * @param max the maximum value
         * @return this builder
         */
        public Builder max(int max) {
            this.max = max;
            return this;
        }

        @Override
        public NormalIntegerGenerator build() {
            if (standardDeviation < 0) {
                throw new IllegalArgumentException("standardDeviation must be non-negative: " + standardDeviation);
            }
            if (max < min) {
                throw new IllegalArgumentException("max (" + max + ") must be >= min (" + min + ")");
            }
            return new NormalIntegerGenerator(this);
        }
    }

    private NormalIntegerGenerator(Builder builder) {
        super(builder.random);
        this.mean = builder.mean;
        this.standardDeviation = builder.standardDeviation;
        this.min = builder.min;
        this.max = builder.max;
    }
}
