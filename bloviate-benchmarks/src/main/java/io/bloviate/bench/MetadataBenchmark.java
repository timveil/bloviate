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

import io.bloviate.db.Database;
import io.bloviate.util.DatabaseUtils;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

/**
 * Cost of JDBC metadata introspection — {@link DatabaseUtils#getMetadata(Connection)}, which walks
 * {@link java.sql.DatabaseMetaData} to build the {@link Database}/{@code Table}/{@code Column} model
 * the fill is planned from. The end-to-end fill benchmarks fold this into total time, where it is
 * invisible because it scales with <em>schema size</em> (table / column / FK count), not row count: on
 * a 16-table fixture it is noise, but on a several-hundred-table schema it can dominate a small fill.
 * This isolates it so that growth is measurable.
 *
 * <p>Runs against an in-memory <strong>H2</strong> database (no Docker): {@link #setup()} creates a
 * synthetic schema of {@link #TABLES} tables, each with {@link #COLUMNS} typed columns, a primary key,
 * and a foreign key into the previous table (a dependency chain, so the FK-metadata path is exercised
 * too). Each measured op re-reads the full metadata. Scale the schema with {@code -Dmeta.tables} and
 * {@code -Dmeta.columns}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MetadataBenchmark {

    /** Tables in the synthetic schema; metadata cost grows with this. */
    private static final int TABLES = Integer.getInteger("meta.tables", 50);

    /** Typed columns per table (beyond the {@code id} primary key and the {@code ref_id} foreign key). */
    private static final int COLUMNS = Integer.getInteger("meta.columns", 16);

    /** A spread of column types so {@code getColumns} returns varied type metadata, cycled per column. */
    private static final String[] TYPES = {
            "INTEGER", "BIGINT", "VARCHAR(64)", "VARCHAR(256)", "NUMERIC(12,2)",
            "DOUBLE", "BOOLEAN", "TIMESTAMP", "DATE", "UUID"
    };

    private Connection connection;

    @Setup(Level.Trial)
    public void setup() throws SQLException {
        // in-memory; DB_CLOSE_DELAY=-1 keeps the database alive for the connection's lifetime
        connection = DriverManager.getConnection("jdbc:h2:mem:metabench;DB_CLOSE_DELAY=-1");
        try (Statement statement = connection.createStatement()) {
            for (int t = 0; t < TABLES; t++) {
                statement.execute(createTableDdl(t));
            }
        }
    }

    @Benchmark
    public Database getMetadata() throws SQLException {
        return DatabaseUtils.getMetadata(connection);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Builds the DDL for table {@code t}: an {@code id} primary key, {@link #COLUMNS} typed columns, and
     * (for every table after the first) a {@code ref_id} foreign key into table {@code t-1}, forming a
     * dependency chain.
     */
    private static String createTableDdl(int t) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE t").append(t).append(" (id INTEGER PRIMARY KEY");
        for (int c = 0; c < COLUMNS; c++) {
            ddl.append(", c").append(c).append(' ').append(TYPES[c % TYPES.length]);
        }
        if (t > 0) {
            ddl.append(", ref_id INTEGER, CONSTRAINT fk_t").append(t)
                    .append(" FOREIGN KEY (ref_id) REFERENCES t").append(t - 1).append("(id)");
        }
        return ddl.append(')').toString();
    }
}
