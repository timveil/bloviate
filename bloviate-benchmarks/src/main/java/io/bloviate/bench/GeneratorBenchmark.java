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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.sql.JDBCType;
import java.util.concurrent.TimeUnit;

/**
 * Per-generator throughput of {@link DataGenerator#generate()} and
 * {@link DataGenerator#generateAsString()} across a representative spread of column types.
 *
 * <p>Each case resolves its generator the same way {@link io.bloviate.db.TableFiller} does —
 * {@code DatabaseSupport.getDataGenerator(column, random)} — so the numbers reflect the real
 * generation cost the engine pays per cell. This is the baseline that the issue-#447
 * generator-level micro-optimizations (#2, #5) are measured against.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class GeneratorBenchmark {

    /** Fixed seed so every run generates the same value stream — reproducibility over time. */
    private static final long SEED = 42L;

    /** Representative column types; JMH expands a value-less {@link Param} over every constant. */
    public enum GenCase {
        INTEGER(BenchColumns.column("c", JDBCType.INTEGER, "int4", null, null)),
        BIGINT(BenchColumns.column("c", JDBCType.BIGINT, "int8", null, null)),
        DOUBLE(BenchColumns.column("c", JDBCType.DOUBLE, "float8", null, null)),
        NUMERIC(BenchColumns.column("c", JDBCType.NUMERIC, "numeric", 12, 2)),
        VARCHAR_SHORT(BenchColumns.column("c", JDBCType.VARCHAR, "varchar", 16, null)),
        VARCHAR_LONG(BenchColumns.column("c", JDBCType.VARCHAR, "varchar", 256, null)),
        BOOLEAN(BenchColumns.column("c", JDBCType.BOOLEAN, "bool", null, null)),
        DATE(BenchColumns.column("c", JDBCType.DATE, "date", null, null)),
        TIMESTAMP(BenchColumns.column("c", JDBCType.TIMESTAMP, "timestamp", null, null)),
        BIT(BenchColumns.column("c", JDBCType.BIT, "bit", 8, null)),
        UUID(BenchColumns.column("c", JDBCType.OTHER, "uuid", null, null)),
        JSONB(BenchColumns.column("c", JDBCType.OTHER, "jsonb", null, null));

        private final Column column;

        GenCase(Column column) {
            this.column = column;
        }
    }

    @Param
    public GenCase genCase;

    private DataGenerator<?> generator;

    @Setup
    public void setup() {
        generator = new PostgresSupport().getDataGenerator(genCase.column, RandomGenerators.create(SEED));
    }

    @Benchmark
    public Object generate() {
        return generator.generate();
    }

    @Benchmark
    public String generateAsString() {
        return generator.generateAsString();
    }
}
