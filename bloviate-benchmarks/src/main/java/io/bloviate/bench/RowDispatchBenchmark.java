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

import io.bloviate.db.Column;
import io.bloviate.ext.PostgresSupport;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Models the per-cell dispatch in {@link io.bloviate.db.TableFiller#fill()} (the inner loop at
 * {@code TableFiller.java:149-164}) <em>without</em> JDBC: for each column it does the
 * {@code generatorMap.get(column)} HashMap lookup and calls {@code generate()}, sinking the
 * result into a {@link Blackhole}.
 *
 * <p>This isolates the dispatch overhead the issue-#447 hot-loop micro-optimization (#5) targets
 * — replacing the per-cell {@link Map#get} (keyed on the {@link Column} record's value-based
 * hashCode) with positional array indexing — giving that change a clean before/after baseline.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class RowDispatchBenchmark {

    private List<Column> filteredColumns;
    private Map<Column, DataGenerator<?>> generatorMap;
    private DataGenerator<?>[] generators;

    @Setup
    public void setup() {
        filteredColumns = BenchColumns.wideRow();

        // mirror TableFiller's setup: one seeded generator per column, resolved through DatabaseSupport
        generatorMap = new HashMap<>();
        generators = new DataGenerator<?>[filteredColumns.size()];
        PostgresSupport support = new PostgresSupport();
        long seed = 1L;
        for (int i = 0; i < filteredColumns.size(); i++) {
            Column column = filteredColumns.get(i);
            DataGenerator<?> generator = support.getDataGenerator(column, RandomGenerators.create(seed++));
            generatorMap.put(column, generator);
            generators[i] = generator;
        }
    }

    /**
     * The original per-cell dispatch: a {@code generatorMap.get(column)} HashMap lookup (hashing the
     * value-based {@link Column} record) followed by {@code generate()}. This is the #447 baseline.
     */
    @Benchmark
    public void dispatchRow(Blackhole blackhole) {
        for (Column column : filteredColumns) {
            DataGenerator<?> generator = generatorMap.get(column);
            blackhole.consume(generator.generate());
        }
    }

    /**
     * The optimized per-cell dispatch: generators are indexed by column position, so the inner loop
     * does a plain array read instead of hashing the {@link Column} on every cell — exactly the
     * change applied to {@link io.bloviate.db.TableFiller#fill()}. Compare against {@link #dispatchRow}
     * to isolate the lookup cost removed (the rest is the unavoidable {@code generate()} work).
     */
    @Benchmark
    public void dispatchRowIndexed(Blackhole blackhole) {
        for (int i = 0; i < generators.length; i++) {
            blackhole.consume(generators[i].generate());
        }
    }
}
