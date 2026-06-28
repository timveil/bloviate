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
 * Generates {@code double} values from a <em>normal (Gaussian)</em> distribution with the configured
 * mean and standard deviation, clamped to the inclusive range {@code [min, max]}. Useful when a
 * numeric column should cluster around a central value rather than spread uniformly (prices, scores,
 * measurements).
 *
 * <p>Clamping (rather than re-drawing) keeps generation O(1) and reproducible; values beyond the
 * bounds pile up slightly at {@code min}/{@code max}, so choose a range a few standard deviations wide
 * to keep that mass negligible.
 *
 * @since 2.11.0
 */
public class NormalDoubleGenerator extends AbstractDataGenerator<Double> {

    private final double mean;
    private final double standardDeviation;
    private final double min;
    private final double max;

    @Override
    public Double generate() {
        double value = random.nextGaussian(mean, standardDeviation);
        return Math.min(max, Math.max(min, value));
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Double value) throws SQLException {
        statement.setDouble(parameterIndex, value);
    }

    @Override
    public Double get(ResultSet resultSet, int columnIndex) throws SQLException {
        double value = resultSet.getDouble(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    /** Builds a {@link NormalDoubleGenerator}, defaulting to a standard normal distribution (mean 0, standard deviation 1) with effectively unbounded {@code min}/{@code max}. */
    public static class Builder extends AbstractBuilder<Double> {

        private double mean = 0.0;
        private double standardDeviation = 1.0;
        private double min = -Double.MAX_VALUE;
        private double max = Double.MAX_VALUE;

        /**
         * Creates a builder drawing from the given random source.
         *
         * @param random the random source backing the generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the distribution mean (center). Returns this builder for chaining.
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
         * Sets the inclusive lower bound that generated values are clamped to. Returns this builder for chaining.
         *
         * @param min the minimum value
         * @return this builder
         */
        public Builder min(double min) {
            this.min = min;
            return this;
        }

        /**
         * Sets the inclusive upper bound that generated values are clamped to. Returns this builder for chaining.
         *
         * @param max the maximum value
         * @return this builder
         */
        public Builder max(double max) {
            this.max = max;
            return this;
        }

        @Override
        public NormalDoubleGenerator build() {
            if (standardDeviation < 0) {
                throw new IllegalArgumentException("standardDeviation must be non-negative: " + standardDeviation);
            }
            if (max < min) {
                throw new IllegalArgumentException("max (" + max + ") must be >= min (" + min + ")");
            }
            return new NormalDoubleGenerator(this);
        }
    }

    private NormalDoubleGenerator(Builder builder) {
        super(builder.random);
        this.mean = builder.mean;
        this.standardDeviation = builder.standardDeviation;
        this.min = builder.min;
        this.max = builder.max;
    }
}
