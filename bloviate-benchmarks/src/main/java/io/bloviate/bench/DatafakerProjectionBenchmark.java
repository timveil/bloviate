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

import io.bloviate.datafaker.People;
import io.bloviate.datafaker.Person;
import io.bloviate.datafaker.RowContext;
import io.bloviate.gen.DataGenerator;
import io.bloviate.util.RandomGenerators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Locale;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;

/**
 * Cost of the issue-#473 correlated row projection in {@code bloviate-datafaker}: several columns
 * projecting fields of one coherent per-row entity ({@link Person}). A "row" here materializes a
 * {@link Person} (a Datafaker build) and projects five fields from it.
 *
 * <p>The two benchmarks are a before/after on the shared-entity design, mirroring how
 * {@link RowDispatchBenchmark} contrasts map vs indexed dispatch:
 * <ul>
 *   <li>{@link #projectRowShared(Blackhole)} — five sibling projections of <em>one</em>
 *       {@link RowContext}. The first projection builds the row's {@link Person}; the other four hit
 *       the context's per-row cache. This is what the engine pays for five correlated person columns.</li>
 *   <li>{@link #projectRowUncorrelated(Blackhole)} — the same five columns, but each backed by its
 *       <em>own</em> context, so every column rebuilds its own {@link Person} every row (five Datafaker
 *       builds per row). This is the cost without the correlation cache.</li>
 * </ul>
 * The gap between them is exactly the work the shared-entity memo removes.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DatafakerProjectionBenchmark {

    /** Fixed seed so every run materializes the same identities — reproducibility over time. */
    private static final long SEED = 42L;

    /** Fixed locale (not the JVM default), matching the module's reproducibility contract. */
    private static final Locale LOCALE = Locale.ENGLISH;

    // five sibling projections of ONE shared person context: one entity build per row, four cache hits
    private DataGenerator<?>[] sharedProjections;

    // five projections, each backed by its OWN context: five entity builds per row (no correlation cache)
    private DataGenerator<?>[] uncorrelatedProjections;

    @Setup
    public void setup() {
        RowContext<Person> shared = People.context(SEED, LOCALE);
        sharedProjections = new DataGenerator<?>[]{
                projection(shared, Person::firstName),
                projection(shared, Person::lastName),
                projection(shared, Person::fullName),
                projection(shared, Person::email),
                projection(shared, Person::username)
        };

        // distinct seeds so the contexts are genuinely independent (no accidental shared cache)
        uncorrelatedProjections = new DataGenerator<?>[]{
                projection(People.context(SEED, LOCALE), Person::firstName),
                projection(People.context(SEED + 1, LOCALE), Person::lastName),
                projection(People.context(SEED + 2, LOCALE), Person::fullName),
                projection(People.context(SEED + 3, LOCALE), Person::email),
                projection(People.context(SEED + 4, LOCALE), Person::username)
        };
    }

    /**
     * One correlated row through five sibling projections of a single shared context: the first
     * projection materializes the {@link Person}, the remaining four hit the per-row cache.
     */
    @Benchmark
    public void projectRowShared(Blackhole blackhole) {
        for (DataGenerator<?> projection : sharedProjections) {
            blackhole.consume(projection.generate());
        }
    }

    /**
     * The same five columns with no shared context, so each column rebuilds its own {@link Person}
     * every row. Compare against {@link #projectRowShared(Blackhole)} to isolate what the shared-entity
     * memo saves.
     */
    @Benchmark
    public void projectRowUncorrelated(Blackhole blackhole) {
        for (DataGenerator<?> projection : uncorrelatedProjections) {
            blackhole.consume(projection.generate());
        }
    }

    private static DataGenerator<?> projection(RowContext<Person> context, Function<Person, String> selector) {
        // project(...) returns a ColumnGeneratorFactory; the per-column random is ignored by the projection
        return context.project(selector).create(RandomGenerators.create(SEED));
    }
}
