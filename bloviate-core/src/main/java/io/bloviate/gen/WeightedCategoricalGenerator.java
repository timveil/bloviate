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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Draws from a fixed set of values according to per-value <em>weights</em> — a categorical
 * distribution. For example a {@code status} column that should be 80% {@code ACTIVE}, 15%
 * {@code CHURNED}, 5% {@code BANNED} instead of three equally-likely codes.
 *
 * <p>Weights need not sum to 1; each value's probability is its weight divided by the total. The
 * value is bound with {@code setObject}, so this works for any column type whose values you supply
 * as the matching Java type (strings, integers, etc.).
 *
 * <p><strong>Reproducibility.</strong> The candidate values are arranged in a stable canonical order
 * (by {@code String.valueOf(value)}) before the cumulative weights are built, so the value returned
 * for a given draw is independent of how the weight map was constructed (e.g. an unordered
 * {@link Map#of()} ). Same seed ⇒ same sequence.
 *
 * @param <T> the value type
 * @since 2.11.0
 */
public class WeightedCategoricalGenerator<T> extends AbstractDataGenerator<T> {

    private final Object[] values;
    private final double[] cumulativeWeights;
    private final double totalWeight;

    @Override
    @SuppressWarnings("unchecked")
    public T generate() {
        double draw = randomUtils.nextDouble(0.0, totalWeight);
        int index = indexFor(draw);
        return (T) values[index];
    }

    /** Binary search for the first cumulative-weight bucket strictly greater than {@code draw}. */
    private int indexFor(double draw) {
        int low = 0;
        int high = cumulativeWeights.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (draw < cumulativeWeights[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(ResultSet resultSet, int columnIndex) throws SQLException {
        return (T) resultSet.getObject(columnIndex);
    }

    /**
     * Fluent builder for {@link WeightedCategoricalGenerator}.
     *
     * @param <T> the category value type produced by the generator
     */
    public static class Builder<T> extends AbstractBuilder<T> {

        private record Entry<T>(T value, double weight) {
        }

        private final List<Entry<T>> entries = new ArrayList<>();

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Adds one value with its (positive) weight. Calls accumulate, so this can be chained.
         *
         * @param value  the value to emit
         * @param weight the relative weight of the value; must be positive
         * @return this builder, for chaining
         */
        public Builder<T> add(T value, double weight) {
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be positive: " + weight);
            }
            entries.add(new Entry<>(value, weight));
            return this;
        }

        /**
         * Adds every value/weight pair from the map. Order is irrelevant — the generator imposes a
         * stable canonical order so output is reproducible regardless of the map's iteration order.
         *
         * @param weights a map of values to their relative (positive) weights
         * @return this builder, for chaining
         */
        public Builder<T> weights(Map<? extends T, ? extends Number> weights) {
            for (Map.Entry<? extends T, ? extends Number> entry : weights.entrySet()) {
                add(entry.getKey(), entry.getValue().doubleValue());
            }
            return this;
        }

        @Override
        public WeightedCategoricalGenerator<T> build() {
            if (entries.isEmpty()) {
                throw new IllegalStateException("at least one weighted value is required");
            }
            return new WeightedCategoricalGenerator<>(this);
        }
    }

    private WeightedCategoricalGenerator(Builder<T> builder) {
        super(builder.random);

        // stable canonical order (by string form) so the value for a given draw never depends on the
        // iteration order of the source map — the key to reproducibility across JVMs
        List<Builder.Entry<T>> sorted = new ArrayList<>(builder.entries);
        sorted.sort((a, b) -> String.valueOf(a.value()).compareTo(String.valueOf(b.value())));

        this.values = new Object[sorted.size()];
        this.cumulativeWeights = new double[sorted.size()];
        double running = 0.0;
        for (int i = 0; i < sorted.size(); i++) {
            values[i] = sorted.get(i).value();
            running += sorted.get(i).weight();
            cumulativeWeights[i] = running;
        }
        this.totalWeight = running;
    }
}
