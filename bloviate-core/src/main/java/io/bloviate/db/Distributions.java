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

package io.bloviate.db;

import io.bloviate.gen.NormalDoubleGenerator;
import io.bloviate.gen.NormalIntegerGenerator;
import io.bloviate.gen.SkewedTimestampGenerator;
import io.bloviate.gen.WeightedCategoricalGenerator;
import io.bloviate.gen.ZipfianIntegerGenerator;

import java.time.Instant;
import java.util.Map;

/**
 * Ready-made {@link ColumnGeneratorFactory ColumnGeneratorFactories} for non-uniform value
 * distributions, so a column can opt into a distribution without writing a generator factory:
 *
 * <pre>{@code
 * new TableConfiguration("orders", 100_000, Set.of(
 *     new ColumnConfiguration("status",     Distributions.weighted(Map.of("NEW", 0.7, "SHIPPED", 0.25, "CANCELLED", 0.05))),
 *     new ColumnConfiguration("rating",     Distributions.normalInt(4, 1, 1, 5)),
 *     new ColumnConfiguration("product_id", Distributions.zipfian(10_000)),
 *     new ColumnConfiguration("created_at", Distributions.recentTimestamps())));
 * }</pre>
 *
 * <p>Each factory builds its generator from the engine-supplied, column-seeded {@code random}, so the
 * output is reproducible (same seed ⇒ same data) and composes with foreign-key reseeding and parallel
 * fills exactly like any other generator. These are <em>specified</em> distributions, not distributions
 * learned from real data.
 *
 * @see io.bloviate.gen.WeightedCategoricalGenerator
 * @see io.bloviate.gen.NormalDoubleGenerator
 * @see io.bloviate.gen.NormalIntegerGenerator
 * @see io.bloviate.gen.ZipfianIntegerGenerator
 * @see io.bloviate.gen.SkewedTimestampGenerator
 * @since 2.11.0
 */
public final class Distributions {

    private Distributions() {
    }

    /**
     * A categorical distribution over the given values, each weighted by its (relative) probability.
     *
     * @param weights value → weight; weights need not sum to 1
     * @param <T> the value type (matched to the column, e.g. String or Integer)
     * @return a factory for a {@link WeightedCategoricalGenerator}
     */
    public static <T> ColumnGeneratorFactory weighted(Map<? extends T, ? extends Number> weights) {
        return random -> new WeightedCategoricalGenerator.Builder<T>(random).weights(weights).build();
    }

    /**
     * A normal (Gaussian) {@code double} distribution clamped to {@code [min, max]}.
     *
     * @return a factory for a {@link NormalDoubleGenerator}
     */
    public static ColumnGeneratorFactory normal(double mean, double standardDeviation, double min, double max) {
        return random -> new NormalDoubleGenerator.Builder(random)
                .mean(mean).standardDeviation(standardDeviation).min(min).max(max).build();
    }

    /**
     * A normal (Gaussian) {@code int} distribution, rounded and clamped to {@code [min, max]}.
     *
     * @return a factory for a {@link NormalIntegerGenerator}
     */
    public static ColumnGeneratorFactory normalInt(double mean, double standardDeviation, int min, int max) {
        return random -> new NormalIntegerGenerator.Builder(random)
                .mean(mean).standardDeviation(standardDeviation).min(min).max(max).build();
    }

    /**
     * A Zipfian (power-law) distribution over {@code [1, size]} with the classic exponent {@code 1.0}.
     *
     * @return a factory for a {@link ZipfianIntegerGenerator}
     */
    public static ColumnGeneratorFactory zipfian(int size) {
        return zipfian(1, size, 1.0);
    }

    /**
     * A Zipfian (power-law) distribution over {@code [start, start + size)} with the given exponent.
     *
     * @return a factory for a {@link ZipfianIntegerGenerator}
     */
    public static ColumnGeneratorFactory zipfian(int start, int size, double exponent) {
        return random -> new ZipfianIntegerGenerator.Builder(random)
                .start(start).size(size).exponent(exponent).build();
    }

    /**
     * Recency-skewed timestamps over the generator's default five-year window.
     *
     * @return a factory for a {@link SkewedTimestampGenerator}
     */
    public static ColumnGeneratorFactory recentTimestamps() {
        return random -> new SkewedTimestampGenerator.Builder(random).build();
    }

    /**
     * Recency-skewed timestamps over {@code [start, end]} with the given skew ({@code 1.0} = uniform).
     *
     * @return a factory for a {@link SkewedTimestampGenerator}
     */
    public static ColumnGeneratorFactory recentTimestamps(Instant start, Instant end, double skew) {
        return random -> new SkewedTimestampGenerator.Builder(random)
                .start(start).end(end).skew(skew).build();
    }
}
