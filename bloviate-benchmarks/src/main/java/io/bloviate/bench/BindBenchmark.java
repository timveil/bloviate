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
import io.bloviate.ext.H2Support;
import io.bloviate.gen.DataGenerator;
import io.bloviate.util.RandomGenerators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-row cost of the real per-cell bind in {@link io.bloviate.db.TableFiller#fill()} — the
 * {@code dataGenerator.generateAndSet(connection, ps, col)} at {@code TableFiller.java:258} — isolated
 * from the database round-trip. The end-to-end fill benchmarks fold generation, parameter binding, and
 * the JDBC wire into one number; this keeps the first two and drops the third by binding against an
 * in-memory <strong>H2</strong> {@link PreparedStatement} and never executing it (so there is no Docker
 * and no network).
 *
 * <p>Each op walks the {@linkplain BenchColumns#standardRow() portable standard-type row}, calling
 * {@code generateAndSet} on every column's generator (resolved through {@link H2Support}, exactly as
 * the engine resolves it) to bind a freshly generated value into the statement, then
 * {@link PreparedStatement#clearParameters()} to reset for the next op. The result is the generation +
 * JDBC bind cost per row; compared with {@link RowDispatchBenchmark} (generation only, no bind) the
 * delta is the parameter-binding cost the wire normally hides.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class BindBenchmark {

    /** Fixed seed so every column's generator emits the same value stream — reproducibility over time. */
    private static final long SEED = 42L;

    /** H2 column types matching {@link BenchColumns#standardRow()}, in the same order. */
    private static final String DDL =
            "CREATE TABLE bind_t (" +
                    "c_int INTEGER, c_bigint BIGINT, c_double DOUBLE, c_numeric NUMERIC(12,2), " +
                    "c_varchar VARCHAR(64), c_bool BOOLEAN, c_date DATE, c_ts TIMESTAMP)";

    private Connection connection;
    private PreparedStatement statement;
    private DataGenerator<?>[] generators;

    @Setup(Level.Trial)
    public void setup() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:bindbench;DB_CLOSE_DELAY=-1");
        try (Statement ddl = connection.createStatement()) {
            ddl.execute(DDL);
        }
        statement = connection.prepareStatement(
                "INSERT INTO bind_t VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

        // resolve one seeded generator per column, the same path TableFiller uses
        H2Support support = new H2Support();
        List<Column> columns = BenchColumns.standardRow();
        generators = new DataGenerator<?>[columns.size()];
        long seed = SEED;
        for (int i = 0; i < columns.size(); i++) {
            generators[i] = support.getDataGenerator(columns.get(i), RandomGenerators.create(seed++));
        }
    }

    /** Generate and bind every column of one row into the prepared statement, then reset — no execute. */
    @Benchmark
    public void bindRow() throws SQLException {
        for (int col = 0; col < generators.length; col++) {
            generators[col].generateAndSet(connection, statement, col + 1);
        }
        statement.clearParameters();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws SQLException {
        if (statement != null) {
            statement.close();
        }
        if (connection != null) {
            connection.close();
        }
    }
}
