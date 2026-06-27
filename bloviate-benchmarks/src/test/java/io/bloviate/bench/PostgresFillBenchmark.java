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

import io.bloviate.db.CommitStrategy;
import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.db.TableConfiguration;
import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * End-to-end fill throughput on PostgreSQL: a realistic TPC-C schema and a deliberately wide,
 * FK-free schema (the parallel-table-fill target). Opt-in — run with {@code -Pbench}.
 */
@Tag("benchmark")
class PostgresFillBenchmark extends AbstractFillBenchmark {

    private static PostgreSQLContainer<?> container(String initScript) {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript(initScript);
        // batch rewrite is on by default; -Dbench.rewriteBatched=false measures the naive baseline
        if (!"false".equalsIgnoreCase(System.getProperty("bench.rewriteBatched", "true"))) {
            container = container.withUrlParam("reWriteBatchedInserts", "true");
        }
        return container;
    }

    /** Commit strategy for the single-table progression, from {@code -Dbench.commit} (none|perTable|everyN:K). */
    private static CommitStrategy commitStrategy() {
        String value = System.getProperty("bench.commit", "none").toLowerCase(Locale.ROOT);
        if (value.equals("pertable")) {
            return CommitStrategy.perTable();
        }
        if (value.startsWith("every")) {
            return CommitStrategy.everyNBatches(Integer.parseInt(value.replaceAll("\\D", "")));
        }
        return CommitStrategy.connectionDefault();
    }

    @Override
    protected void truncateAll(Connection connection, List<String> tables) throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE " + String.join(", ", tables) + " CASCADE");
        }
    }

    @Test
    void tpcc() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(
                BATCH_SIZE, 0, new PostgresSupport(), tpccTables(), SEED);

        try (PostgreSQLContainer<?> database = container("create_tpcc.postgres.sql")) {
            database.start();
            runBenchmark("postgres/tpcc", database, configuration);
        }
    }

    @Test
    void wide() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(
                BATCH_SIZE, ROWS, new PostgresSupport(), new HashSet<>(), SEED);

        try (PostgreSQLContainer<?> database = container("create_wide.postgres.sql")) {
            database.start();
            runBenchmark("postgres/wide", database, configuration);
        }
    }

    /**
     * Single dominant table: between-table parallelism cannot help (one table, one level), so only
     * intra-table partitioning (issue #466) speeds it up. Doubles as the <em>cumulative progression</em>
     * fixture — layer the #447 follow-ups one at a time via system properties to watch throughput
     * compound:
     * <ul>
     *   <li>{@code -Dbench.rewriteBatched=false|true} — driver batch rewrite (#468), default {@code true}</li>
     *   <li>{@code -Dbench.commit=none|perTable|everyN:K} — commit strategy (#467), default {@code none}</li>
     *   <li>{@code -Dbench.threads=N} / {@code -Dbench.partitions=N} — intra-table partitioning (#466)</li>
     * </ul>
     * Reproducibility is per configuration: the partitioned non-key values differ from the sequential
     * ones by design.
     */
    @Test
    void singleTable() throws SQLException {
        int partitions = Integer.getInteger("bench.partitions", Math.max(2, THREADS));
        Set<TableConfiguration> tables = Set.of(new TableConfiguration("big_t", ROWS, partitions));
        DatabaseConfiguration configuration = new DatabaseConfiguration(
                BATCH_SIZE, ROWS, new PostgresSupport(), tables, SEED, commitStrategy());

        try (PostgreSQLContainer<?> database = container("create_single.postgres.sql")) {
            database.start();
            runBenchmark("postgres/single", database, configuration);
        }
    }
}
