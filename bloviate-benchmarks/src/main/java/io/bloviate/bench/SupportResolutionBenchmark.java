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
import io.bloviate.ext.CockroachDBSupport;
import io.bloviate.ext.DatabaseSupport;
import io.bloviate.ext.DefaultSupport;
import io.bloviate.ext.H2Support;
import io.bloviate.ext.MariaDBSupport;
import io.bloviate.ext.MySQLSupport;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.ext.SQLiteSupport;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.concurrent.TimeUnit;

/**
 * Generator-<em>resolution</em> dispatch across every {@link DatabaseSupport} — the
 * {@code getDataGenerator(column, random)} registry lookup plus builder construction that
 * {@link io.bloviate.db.TableFiller} pays once per column when it sets a table up. {@link GeneratorBenchmark}
 * only ever resolves through {@link PostgresSupport}; this crosses all seven supports so the per-support
 * dispatch cost is comparable and every support's resolution path is exercised.
 *
 * <p>It resolves the {@linkplain BenchColumns#standardRow() portable standard-type row} only: those
 * types resolve through the shared cross-database defaults, so every support can resolve every column
 * and the numbers compare like-for-like. The database-specific extension types (PostgreSQL {@code uuid},
 * {@code jsonb}, arrays, …) are not portable across supports and are covered, per type, by
 * {@link GeneratorBenchmark}.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class SupportResolutionBenchmark {

    /** Fixed seed; the random is only captured by the resolved generators, not consumed during resolution. */
    private static final long SEED = 42L;

    /** Every built-in {@link DatabaseSupport}; JMH expands a value-less {@link Param} over each constant. */
    public enum SupportCase {
        POSTGRES(new PostgresSupport()),
        MYSQL(new MySQLSupport()),
        COCKROACH(new CockroachDBSupport()),
        MARIADB(new MariaDBSupport()),
        H2(new H2Support()),
        SQLITE(new SQLiteSupport()),
        DEFAULT(new DefaultSupport());

        private final DatabaseSupport support;

        SupportCase(DatabaseSupport support) {
            this.support = support;
        }
    }

    @Param
    public SupportCase supportCase;

    private List<Column> columns;
    private RandomGenerator random;

    @Setup
    public void setup() {
        columns = BenchColumns.standardRow();
        random = RandomGenerators.create(SEED);
    }

    /** Resolves a generator for every column of the standard row — the per-row resolution cost. */
    @Benchmark
    public void resolveRow(Blackhole blackhole) {
        DatabaseSupport support = supportCase.support;
        for (Column column : columns) {
            blackhole.consume(support.getDataGenerator(column, random));
        }
    }
}
