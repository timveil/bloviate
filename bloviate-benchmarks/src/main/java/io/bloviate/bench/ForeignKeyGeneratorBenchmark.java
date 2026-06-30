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

import io.bloviate.gen.ChildCardinality;
import io.bloviate.gen.ChildCountGenerator;
import io.bloviate.gen.ChildKeyComponentGenerator;
import io.bloviate.gen.CompositeKeyComponentGenerator;
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

import java.util.random.RandomGenerator;
import java.util.concurrent.TimeUnit;

/**
 * Per-row cost of the cross-table key generation that referentially-correct fills pay and FK-free
 * fills don't — the work that makes a TPC-C-shaped fill more expensive per row than the {@code wide}
 * fixture. It is pure CPU (the key generators reproduce the parent key space arithmetically and never
 * read parent rows back), so it is isolated here with no JDBC.
 *
 * <p>The model is a TPC-C {@code orders} → {@code order_line} relationship keyed on
 * {@code (w_id, d_id, o_id[, ol_number])}: a parent {@code orders} row carries a composite key plus a
 * "number of children" column, and each child {@code order_line} row reproduces its parent's composite
 * key and adds a per-parent sequence number. A single shared {@link ChildCardinality} is the source of
 * truth for how many children each parent owns, exactly as the engine wires it.
 *
 * <ul>
 *   <li>{@link #parentKeyRow(Blackhole)} — one parent row: three {@link CompositeKeyComponentGenerator}
 *       key dimensions plus a {@link ChildCountGenerator} (which calls {@code cardinality.count}).</li>
 *   <li>{@link #childKeyRow(Blackhole)} — one child row: three parent-reproducing
 *       {@link ChildKeyComponentGenerator} dimensions plus a sequence-mode one. These share the
 *       cardinality and advance in lockstep (the engine generates each key column once per row), so a
 *       call to all four is exactly one child row; each {@code generate()} is {@code synchronized} and
 *       walks the per-parent child count.</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class ForeignKeyGeneratorBenchmark {

    /** Fixed seed so the cardinality (and thus the whole walk) is identical every run. */
    private static final long SEED = 42L;

    // a modest TPC-C-shaped scale; the per-row cost is arithmetic + a hash, so exact sizes barely move it
    private static final int WAREHOUSES = 10;
    private static final int DISTRICTS = 10;
    private static final int ORDERS = 3_000;        // orders per district
    private static final int MIN_LINES = 5;
    private static final int MAX_LINES = 15;

    /** Parent composite-key dimensions plus the parent's child-count column. */
    private DataGenerator<?>[] parentRow;

    /** Child key: parent-reproducing dimensions plus the per-parent sequence number. */
    private DataGenerator<?>[] childRow;

    @Setup
    public void setup() {
        // one shared cardinality: how many order_lines each order owns
        ChildCardinality cardinality = new ChildCardinality(MIN_LINES, MAX_LINES, SEED);

        // parent orders key (w_id, d_id, o_id), o_id innermost, plus o_ol_cnt (the child-count column)
        parentRow = new DataGenerator<?>[]{
                composite(1L, WAREHOUSES, (long) ORDERS * DISTRICTS),                 // w_id
                composite(1L, DISTRICTS, ORDERS),                                     // d_id
                composite(1L, ORDERS, 1L),                                            // o_id (innermost)
                new ChildCountGenerator.Builder(rng()).cardinality(cardinality).build()
        };

        // child order_line key: reproduce the parent (w_id, d_id, o_id), then the sequence ol_number
        childRow = new DataGenerator<?>[]{
                childComponent(cardinality, 1, WAREHOUSES, (long) ORDERS * DISTRICTS), // ol_w_id
                childComponent(cardinality, 1, DISTRICTS, ORDERS),                     // ol_d_id
                childComponent(cardinality, 1, ORDERS, 1L),                            // ol_o_id
                new ChildKeyComponentGenerator.Builder(rng()).cardinality(cardinality).sequence().start(1).build()
        };
    }

    /** One parent {@code orders} row's key columns plus its child-count column. */
    @Benchmark
    public void parentKeyRow(Blackhole blackhole) {
        for (DataGenerator<?> component : parentRow) {
            blackhole.consume(component.generate());
        }
    }

    /** One child {@code order_line} row's foreign-key columns (parent reproduction + sequence). */
    @Benchmark
    public void childKeyRow(Blackhole blackhole) {
        for (DataGenerator<?> component : childRow) {
            blackhole.consume(component.generate());
        }
    }

    private static CompositeKeyComponentGenerator composite(long start, int cycle, long repeat) {
        return new CompositeKeyComponentGenerator.Builder(rng())
                .start((int) start).cycle(cycle).repeat(repeat).build();
    }

    private static ChildKeyComponentGenerator childComponent(ChildCardinality cardinality, int start, int cycle, long repeat) {
        return new ChildKeyComponentGenerator.Builder(rng())
                .cardinality(cardinality).start(start).cycle(cycle).repeat(repeat).build();
    }

    /** The key generators ignore their random source (keys are positional), but the builders require one. */
    private static RandomGenerator rng() {
        return RandomGenerators.create(SEED);
    }
}
