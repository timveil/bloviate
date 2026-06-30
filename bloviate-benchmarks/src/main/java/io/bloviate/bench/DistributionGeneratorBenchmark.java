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

package io.bloviate.bench;

import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.NormalDoubleGenerator;
import io.bloviate.gen.NormalIntegerGenerator;
import io.bloviate.gen.SkewedTimestampGenerator;
import io.bloviate.gen.WeightedCategoricalGenerator;
import io.bloviate.gen.ZipfianIntegerGenerator;
import io.bloviate.util.RandomGenerators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.concurrent.TimeUnit;

/**
 * Per-draw throughput of the <em>distribution</em> generators — the non-uniform shapes you reach for
 * to make data realistic (skewed foreign-key references, weighted categories, bell-curve numerics,
 * recency-biased timestamps). {@link GeneratorBenchmark} covers only uniform type-driven generators;
 * these are config-driven (opt-in per column), so they are resolved by their own builders rather than
 * through {@code DatabaseSupport.getDataGenerator}, and benchmarked here.
 *
 * <p>What it isolates is the per-draw sampling cost each distribution adds over a plain uniform draw:
 * the cumulative-weight binary search for {@link ZipfianIntegerGenerator} (over a large key space, its
 * CDF is built once at construction — in {@link #setup()}, not timed) and
 * {@link WeightedCategoricalGenerator}, the {@code nextGaussian}/clamp of the normal generators, and
 * the {@code pow}-reshape plus {@link java.sql.Timestamp} construction of
 * {@link SkewedTimestampGenerator}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DistributionGeneratorBenchmark {

    /** Fixed seed so every run draws the same value stream — reproducibility over time. */
    private static final long SEED = 42L;

    /** A representative key-space size for the rank-based generators (Zipfian builds its CDF over this). */
    private static final int KEY_SPACE = 1_000_000;

    /** Distribution generators, each built directly since they are configured, not type-resolved. */
    public enum DistCase {
        // classic Zipf and a steeper variant — exercises the cumulative-weight binary search per draw
        ZIPFIAN(r -> new ZipfianIntegerGenerator.Builder(r).start(1).size(KEY_SPACE).exponent(1.0).build()),
        ZIPFIAN_STEEP(r -> new ZipfianIntegerGenerator.Builder(r).start(1).size(KEY_SPACE).exponent(1.5).build()),
        // a small weighted category set, the common "status"/"type" column shape
        WEIGHTED_CATEGORICAL(DistributionGeneratorBenchmark::weightedCategorical),
        // bell-curve numerics: nextGaussian + clamp, integer and double
        NORMAL_DOUBLE(r -> new NormalDoubleGenerator.Builder(r).mean(100).standardDeviation(15).min(0).max(200).build()),
        NORMAL_INTEGER(r -> new NormalIntegerGenerator.Builder(r).mean(100).standardDeviation(15).min(0).max(200).build()),
        // recency-biased timestamps: pow-reshape of a uniform draw across the default window
        SKEWED_TIMESTAMP(r -> new SkewedTimestampGenerator.Builder(r).skew(3.0).build());

        private final Function<RandomGenerator, DataGenerator<?>> factory;

        DistCase(Function<RandomGenerator, DataGenerator<?>> factory) {
            this.factory = factory;
        }
    }

    @Param
    public DistCase distCase;

    private DataGenerator<?> generator;

    @Setup
    public void setup() {
        generator = distCase.factory.apply(RandomGenerators.create(SEED));
    }

    @Benchmark
    public Object generate() {
        return generator.generate();
    }

    @Benchmark
    public String generateAsString() {
        return generator.generateAsString();
    }

    /** A small, representative weighted category set (skewed toward the head category). */
    private static DataGenerator<?> weightedCategorical(RandomGenerator random) {
        return new WeightedCategoricalGenerator.Builder<String>(random)
                .add("NEW", 50)
                .add("OPEN", 25)
                .add("PENDING", 12)
                .add("CLOSED", 8)
                .add("CANCELLED", 3)
                .add("ARCHIVED", 2)
                .build();
    }
}
