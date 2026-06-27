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

import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the issue #479 (Part A) distribution generators: each produces the intended
 * <em>shape</em>, stays within bounds, and is reproducible (same seed ⇒ identical sequence).
 */
class DistributionGeneratorsTest {

    private static final long SEED = 42L;
    private static final int N = 50_000;

    /** Same seed ⇒ identical sequence, for any generator factory. */
    private static <T> void assertReproducible(Supplier<DataGenerator<T>> factory) {
        DataGenerator<T> a = factory.get();
        DataGenerator<T> b = factory.get();
        for (int i = 0; i < 1_000; i++) {
            assertEquals(a.generate(), b.generate(), "same seed must produce the same value at draw " + i);
        }
    }

    @Test
    void weightedCategoricalMatchesWeightsAndIsReproducible() {
        Map<String, Double> weights = Map.of("ACTIVE", 0.8, "CHURNED", 0.15, "BANNED", 0.05);
        assertReproducible(() -> new WeightedCategoricalGenerator.Builder<String>(RandomGenerators.create(SEED))
                .weights(weights).build());

        WeightedCategoricalGenerator<String> generator =
                new WeightedCategoricalGenerator.Builder<String>(RandomGenerators.create(SEED)).weights(weights).build();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < N; i++) {
            counts.merge(generator.generate(), 1, Integer::sum);
        }
        // every category appears, and observed frequencies track the configured weights (loose bounds)
        assertEquals(3, counts.size());
        assertTrue(counts.get("ACTIVE") > counts.get("CHURNED"), "ACTIVE (0.8) should dominate CHURNED (0.15)");
        assertTrue(counts.get("CHURNED") > counts.get("BANNED"), "CHURNED (0.15) should beat BANNED (0.05)");
        double activeShare = counts.get("ACTIVE") / (double) N;
        assertTrue(activeShare > 0.75 && activeShare < 0.85, "ACTIVE share ~0.80 but was " + activeShare);
    }

    @Test
    void weightedCategoricalOrderIndependentOfMapType() {
        // a LinkedHashMap in a different insertion order must yield the SAME sequence as Map.of,
        // because the generator imposes a stable canonical order — the reproducibility guarantee
        Map<String, Double> reversed = new java.util.LinkedHashMap<>();
        reversed.put("BANNED", 0.05);
        reversed.put("CHURNED", 0.15);
        reversed.put("ACTIVE", 0.8);

        WeightedCategoricalGenerator<String> fromOrdered =
                new WeightedCategoricalGenerator.Builder<String>(RandomGenerators.create(SEED))
                        .weights(Map.of("ACTIVE", 0.8, "CHURNED", 0.15, "BANNED", 0.05)).build();
        WeightedCategoricalGenerator<String> fromReversed =
                new WeightedCategoricalGenerator.Builder<String>(RandomGenerators.create(SEED))
                        .weights(reversed).build();

        for (int i = 0; i < 1_000; i++) {
            assertEquals(fromOrdered.generate(), fromReversed.generate(), "map order must not affect output");
        }
    }

    @Test
    void normalIntegerStaysInRangeAndCentersOnMean() {
        assertReproducible(() -> new NormalIntegerGenerator.Builder(RandomGenerators.create(SEED))
                .mean(50).standardDeviation(10).min(0).max(100).build());

        NormalIntegerGenerator generator = new NormalIntegerGenerator.Builder(RandomGenerators.create(SEED))
                .mean(50).standardDeviation(10).min(0).max(100).build();
        long sum = 0;
        for (int i = 0; i < N; i++) {
            int value = generator.generate();
            assertTrue(value >= 0 && value <= 100, "value out of [0,100]: " + value);
            sum += value;
        }
        double mean = sum / (double) N;
        assertTrue(mean > 48 && mean < 52, "sample mean ~50 but was " + mean);
    }

    @Test
    void normalDoubleStaysInRange() {
        NormalDoubleGenerator generator = new NormalDoubleGenerator.Builder(RandomGenerators.create(SEED))
                .mean(0).standardDeviation(1).min(-2).max(2).build();
        for (int i = 0; i < N; i++) {
            double value = generator.generate();
            assertTrue(value >= -2.0 && value <= 2.0, "value out of [-2,2]: " + value);
        }
    }

    @Test
    void zipfianConcentratesOnTheHeadAndIsReproducible() {
        assertReproducible(() -> new ZipfianIntegerGenerator.Builder(RandomGenerators.create(SEED))
                .start(1).size(100).exponent(1.0).build());

        ZipfianIntegerGenerator generator = new ZipfianIntegerGenerator.Builder(RandomGenerators.create(SEED))
                .start(1).size(100).exponent(1.0).build();
        int[] counts = new int[101];
        for (int i = 0; i < N; i++) {
            int value = generator.generate();
            assertTrue(value >= 1 && value <= 100, "value out of [1,100]: " + value);
            counts[value]++;
        }
        assertTrue(counts[1] > counts[2], "rank 1 should beat rank 2");
        assertTrue(counts[1] > counts[100] * 10, "the head should dwarf the tail");
    }

    @Test
    void skewedTimestampStaysInWindowAndLeansRecent() {
        Instant start = Instant.parse("2020-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-01T00:00:00Z");
        assertReproducible(() -> new SkewedTimestampGenerator.Builder(RandomGenerators.create(SEED))
                .start(start).end(end).skew(3.0).build());

        SkewedTimestampGenerator generator = new SkewedTimestampGenerator.Builder(RandomGenerators.create(SEED))
                .start(start).end(end).skew(3.0).build();
        long startMs = start.toEpochMilli();
        long rangeMs = end.toEpochMilli() - startMs;
        double fractionSum = 0;
        List<Timestamp> samples = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Timestamp ts = generator.generate();
            long ms = ts.getTime();
            assertTrue(ms >= startMs && ms <= end.toEpochMilli(), "timestamp out of window: " + ts);
            fractionSum += (ms - startMs) / (double) rangeMs;
            if (i < 5) {
                samples.add(ts);
            }
        }
        // with skew 3.0 the average position should sit well past the midpoint (recent-weighted)
        double meanFraction = fractionSum / N;
        assertTrue(meanFraction > 0.6, "recency-skewed mean fraction should exceed 0.6 but was " + meanFraction);
    }

    @Test
    void buildersRejectInvalidArguments() {
        var random = RandomGenerators.create(SEED);
        assertThrows(IllegalStateException.class,
                () -> new WeightedCategoricalGenerator.Builder<String>(random).build());
        assertThrows(IllegalArgumentException.class,
                () -> new NormalIntegerGenerator.Builder(random).min(10).max(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> new ZipfianIntegerGenerator.Builder(random).size(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> new SkewedTimestampGenerator.Builder(random).skew(0).build());
    }
}
