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
 * Generates integers from a <em>Zipfian</em> (power-law) distribution over {@code size} consecutive
 * values starting at {@code start}: rank <em>k</em> (1-based) has probability proportional to
 * {@code 1 / k^exponent}, so the lowest values are by far the most frequent and the tail is long and
 * thin. This is the shape of "popularity" columns — a few hot keys, many rare ones (referenced
 * products, authors, tags) — that uniform random generation never reproduces.
 *
 * <p>A cumulative table of {@code size} doubles is precomputed once, so keep {@code size} modest
 * (thousands to low millions). {@code exponent} defaults to {@code 1.0} (classic Zipf); higher values
 * concentrate mass even harder on the head.
 *
 * @since 2.11.0
 */
public class ZipfianIntegerGenerator extends AbstractDataGenerator<Integer> {

    /**
     * The largest supported {@link Builder#size(int)}. Construction precomputes a cumulative table of
     * {@code size} {@code double}s ({@code 8 * size} bytes), so an unbounded {@code size} would OOM the
     * JVM at construction time (e.g. {@code size = 2_000_000_000} would request ~16&nbsp;GB). This cap
     * keeps the table to ~400&nbsp;MB and fails fast with a clear message above it; the distribution is
     * intended for value spaces of thousands to low millions anyway.
     */
    public static final int MAX_SIZE = 50_000_000;

    private final int start;
    private final double[] cumulativeWeights;
    private final double totalWeight;

    @Override
    public Integer generate() {
        double draw = randomUtils.nextDouble(0.0, totalWeight);
        return start + Cdf.upperBound(cumulativeWeights, draw);
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

    /** Fluent builder for {@link ZipfianIntegerGenerator}. */
    public static class Builder extends AbstractBuilder<Integer> {

        private int start = 1;
        private int size = 0;
        private double exponent = 1.0;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * The first (most frequent) value; ranks run {@code [start, start + size)}. Default {@code 1}.
         *
         * @param start the lowest rank value
         * @return this builder, for chaining
         */
        public Builder start(int start) {
            this.start = start;
            return this;
        }

        /**
         * The number of distinct values; required and must be in {@code [1, }{@link #MAX_SIZE}{@code ]}.
         *
         * @param size the count of distinct ranks
         * @return this builder, for chaining
         */
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        /**
         * The skew exponent; {@code 1.0} is classic Zipf, higher concentrates more on the head.
         *
         * @param exponent the skew exponent
         * @return this builder, for chaining
         */
        public Builder exponent(double exponent) {
            this.exponent = exponent;
            return this;
        }

        @Override
        public ZipfianIntegerGenerator build() {
            if (size < 1) {
                throw new IllegalArgumentException("size must be >= 1: " + size);
            }
            if (size > MAX_SIZE) {
                throw new IllegalArgumentException("size must be <= " + MAX_SIZE + ": " + size);
            }
            if (exponent < 0) {
                throw new IllegalArgumentException("exponent must be non-negative: " + exponent);
            }
            return new ZipfianIntegerGenerator(this);
        }
    }

    private ZipfianIntegerGenerator(Builder builder) {
        super(builder.random);
        this.start = builder.start;
        this.cumulativeWeights = new double[builder.size];
        double running = 0.0;
        for (int k = 1; k <= builder.size; k++) {
            running += 1.0 / Math.pow(k, builder.exponent);
            cumulativeWeights[k - 1] = running;
        }
        this.totalWeight = running;
    }
}
