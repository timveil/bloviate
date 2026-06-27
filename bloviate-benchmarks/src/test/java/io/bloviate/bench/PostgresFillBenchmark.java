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
import java.util.Set;

/**
 * End-to-end fill throughput on PostgreSQL: a realistic TPC-C schema and a deliberately wide,
 * FK-free schema (the parallel-table-fill target). Opt-in — run with {@code -Pbench}.
 */
@Tag("benchmark")
class PostgresFillBenchmark extends AbstractFillBenchmark {

    private static PostgreSQLContainer<?> container(String initScript) {
        return new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript(initScript);
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
     * intra-table partitioning (issue #466) speeds it up. Compare {@code -Dbench.threads=1} (baseline,
     * partitions ignored on the sequential path) against {@code -Dbench.threads=N} (the one table is
     * split into {@code bench.partitions} ranges, default {@code N}). Reproducibility is per
     * configuration: the partitioned non-key values differ from the sequential ones by design.
     */
    @Test
    void singleTable() throws SQLException {
        int partitions = Integer.getInteger("bench.partitions", Math.max(2, THREADS));
        Set<TableConfiguration> tables = Set.of(new TableConfiguration("big_t", ROWS, partitions));
        DatabaseConfiguration configuration = new DatabaseConfiguration(
                BATCH_SIZE, ROWS, new PostgresSupport(), tables, SEED);

        try (PostgreSQLContainer<?> database = container("create_single.postgres.sql")) {
            database.start();
            runBenchmark("postgres/single", database, configuration);
        }
    }
}
