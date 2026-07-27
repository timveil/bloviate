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
import io.bloviate.util.IndexedRandom;
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

import java.sql.JDBCType;
import java.util.concurrent.TimeUnit;

/**
 * Hot-path cost of per-index derivation (issue #553): the engine now creates each column's random
 * source as an {@link IndexedRandom} and repositions it to the absolute row index before every cell,
 * making each value a pure function of {@code (columnSeed, rowIndex)} and partition seeks O(1).
 *
 * <p>Three variants isolate the change:
 * <ul>
 *   <li>{@link #legacySequentialDraws} — the pre-#553 hot path: {@code generate()} drawing
 *       sequentially from {@code L64X128MixRandom} (what {@code RandomGenerators.create} returns).</li>
 *   <li>{@link #indexedSequentialDraws} — the same sequential pattern on {@link IndexedRandom},
 *       isolating the RNG algorithm swap (SplitMix64 stream vs L64X128).</li>
 *   <li>{@link #positionedDraws} — the new hot path: {@code position(rowIndex)} before each
 *       {@code generate()}, exactly what {@code TableFiller} does per cell.</li>
 * </ul>
 * Comparing the last two shows the positioning overhead (one add + bounds check per cell); comparing
 * the first two shows the algorithm swap. The generators are resolved the same way the engine
 * resolves them ({@code DatabaseSupport.getDataGenerator}).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class PositionedGenerationBenchmark {

    /** Fixed seed so every run generates the same value stream — reproducibility over time. */
    private static final long SEED = 42L;

    /** A spread from single-draw scalars to draw-heavy strings, where positioning cost would show. */
    // NonSerializableClass: see GeneratorBenchmark.GenCase -- enums serialise by name, never fields.
    @SuppressWarnings("PMD.NonSerializableClass")
    public enum GenCase {
        INTEGER(BenchColumns.column("c", JDBCType.INTEGER, "int4", null, null)),
        UUID(BenchColumns.column("c", JDBCType.OTHER, "uuid", null, null)),
        NUMERIC(BenchColumns.column("c", JDBCType.NUMERIC, "numeric", 12, 2)),
        VARCHAR_SHORT(BenchColumns.column("c", JDBCType.VARCHAR, "varchar", 16, null)),
        VARCHAR_LONG(BenchColumns.column("c", JDBCType.VARCHAR, "varchar", 256, null));

        final Column column;

        GenCase(Column column) {
            this.column = column;
        }
    }

    @Param
    private GenCase genCase;

    private DataGenerator<?> legacyGenerator;
    private DataGenerator<?> indexedGenerator;
    private DataGenerator<?> positionedGenerator;
    private IndexedRandom positionedRandom;
    private long rowIndex;

    @Setup
    public void setup() {
        PostgresSupport support = new PostgresSupport();
        legacyGenerator = support.getDataGenerator(genCase.column, RandomGenerators.create(SEED));
        indexedGenerator = support.getDataGenerator(genCase.column, new IndexedRandom(SEED));
        positionedRandom = new IndexedRandom(SEED);
        positionedGenerator = support.getDataGenerator(genCase.column, positionedRandom);
        rowIndex = 0;
    }

    /** The pre-#553 per-cell cost: sequential draws from {@code L64X128MixRandom}. */
    @Benchmark
    public Object legacySequentialDraws() {
        return legacyGenerator.generate();
    }

    /** Sequential draws from {@link IndexedRandom}: isolates the RNG algorithm swap. */
    @Benchmark
    public Object indexedSequentialDraws() {
        return indexedGenerator.generate();
    }

    /** The new per-cell cost: reposition to the row index, then generate — what the engine does. */
    @Benchmark
    public Object positionedDraws() {
        positionedRandom.position(rowIndex++);
        return positionedGenerator.generate();
    }
}
