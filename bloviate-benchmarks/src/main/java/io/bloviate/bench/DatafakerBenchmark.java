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

import io.bloviate.datafaker.DatafakerStringGenerator;
import io.bloviate.gen.DataGenerator;
import io.bloviate.util.RandomGenerators;
import net.datafaker.Faker;
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

import java.util.Locale;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

/**
 * Raw per-cell throughput of the {@code bloviate-datafaker} realistic-value layer — the
 * previously-unbenchmarked, and most expensive, way the engine can fill a cell. Each case resolves a
 * {@link DatafakerStringGenerator} over one Datafaker provider (the same providers
 * {@code DatafakerGeneratorPlugin} wires by column name) and times a single
 * {@link DatafakerStringGenerator#generate()}, so the numbers are directly comparable to the
 * type-driven generators in {@link GeneratorBenchmark} — i.e. "what does turning on semantic data cost
 * per column?"
 *
 * <p>The correlated multi-column path (one shared entity projected by several columns) is measured
 * separately by {@link DatafakerProjectionBenchmark}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DatafakerBenchmark {

    /** Fixed seed so every run draws the same realistic value stream — reproducibility over time. */
    private static final long SEED = 42L;

    /** Fixed locale (not the JVM default), matching the module's reproducibility contract. */
    private static final Locale LOCALE = Locale.ENGLISH;

    /** A representative spread of the providers {@code DatafakerGeneratorPlugin} maps by column name. */
    public enum Provider {
        EMAIL(f -> f.internet().safeEmailAddress()),
        FIRST_NAME(f -> f.name().firstName()),
        FULL_NAME(f -> f.name().fullName()),
        PHONE(f -> f.regexify("\\([0-9]{3}\\) 555-01[0-9]{2}")),
        COMPANY(f -> f.company().name()),
        CITY(f -> f.address().city()),
        STREET_ADDRESS(f -> f.address().streetAddress());

        private final Function<Faker, String> valueFunction;

        Provider(Function<Faker, String> valueFunction) {
            this.valueFunction = valueFunction;
        }
    }

    @Param
    public Provider provider;

    private DataGenerator<String> generator;

    @Setup
    public void setup() {
        generator = new DatafakerStringGenerator(RandomGenerators.create(SEED), LOCALE, 255, provider.valueFunction);
    }

    @Benchmark
    public String generate() {
        return generator.generate();
    }
}
