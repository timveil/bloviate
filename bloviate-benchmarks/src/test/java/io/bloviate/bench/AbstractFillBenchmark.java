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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.db.BulkLoadStrategy;
import io.bloviate.db.Database;
import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.db.DatabaseFiller;
import io.bloviate.db.Table;
import io.bloviate.db.TableConfiguration;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import io.bloviate.util.DatabaseUtils;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Repeatable end-to-end fill timing harness — the issue-#447 baseline for the IO/concurrency-bound
 * optimizations (#1 parallel table fill, #3 commit tuning, #4 batch rewrite).
 *
 * <p>Each measured iteration truncates the schema, times a full {@link DatabaseFiller#fill()}
 * (metadata fetch included, exactly what a user pays), and reports throughput as rows/second. A
 * fixed {@link #SEED} makes every iteration generate identical data, so timings are comparable
 * across runs and across optimization branches. JMH is deliberately not used here: it forks JVMs
 * and runs short timed bursts, which fits poorly with multi-second container fills.
 *
 * <p>Tunable via system properties: {@code bench.warmup} (default 1), {@code bench.iterations}
 * (3), {@code bench.rows} (default-row-count for the wide schema, 50,000), {@code bench.warehouses}
 * (TPC-C scale factor, 1), {@code bench.batch} (JDBC batch size, 1000), {@code bench.threads}
 * (parallel workers, 1), {@code bench.bulk} (unordered bulk load, false).
 */
abstract class AbstractFillBenchmark {

    /** Fixed base seed → identical data every iteration and every run (reproducibility). */
    protected static final long SEED = 42L;

    protected static final int WARMUP_FILLS = Integer.getInteger("bench.warmup", 1);
    protected static final int MEASURED_FILLS = Integer.getInteger("bench.iterations", 3);
    protected static final long ROWS = Long.getLong("bench.rows", 50_000L);
    protected static final int BATCH_SIZE = Integer.getInteger("bench.batch", 1000);

    /**
     * Worker threads for parallel table fill (issue #447, item #1). The default of {@code 1} fills
     * sequentially on a single connection — the baseline; a value greater than one fills independent
     * tables concurrently from the pool, one connection per worker. Set with {@code -Dbench.threads}.
     */
    protected static final int THREADS = Integer.getInteger("bench.threads", 1);

    /**
     * Whether to use the unordered bulk-load path (disable foreign-key enforcement, fill every table
     * at once with no topological barrier, re-enable). Only effective with {@code bench.threads > 1}
     * on a database whose support reports {@code supportsBulkLoad()} (PostgreSQL, MySQL). Set with
     * {@code -Dbench.bulk=true}. The win is largest on deep/narrow FK chains (TPC-C); the FK-free wide
     * and single-table fixtures are negative controls expected to stay roughly flat.
     */
    protected static final boolean BULK = Boolean.getBoolean("bench.bulk");

    // TPC-C cardinalities; modest defaults (~60k rows) keep a casual run quick, scale up via -D
    protected static final int WAREHOUSES = Integer.getInteger("bench.warehouses", 1);
    protected static final int ITEMS = Integer.getInteger("bench.items", 10_000);
    protected static final int DISTRICTS = Integer.getInteger("bench.districts", 10);
    protected static final int CUSTOMERS = Integer.getInteger("bench.customers", 300);
    protected static final int MIN_LINES = Integer.getInteger("bench.minLines", 5);
    protected static final int MAX_LINES = Integer.getInteger("bench.maxLines", 15);

    /** TPC-C table configuration at the {@code bench.*} scale, shared by every DB runner. */
    protected static Set<TableConfiguration> tpccTables() {
        return TPCCConfiguration.build(WAREHOUSES, ITEMS, DISTRICTS, CUSTOMERS, MIN_LINES, MAX_LINES);
    }

    protected static HikariDataSource dataSource(JdbcDatabaseContainer<?> container) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setDriverClassName(container.getDriverClassName());
        // the parallel fill borrows one connection per worker (plus the harness's own); size the
        // pool so workers never block waiting for a connection
        config.setMaximumPoolSize(Math.max(10, THREADS + 2));
        return new HikariDataSource(config);
    }

    /**
     * DB-specific bulk clear of all user tables between fills (foreign keys make ordering matter,
     * so each database overrides this with the right cascade / FK-check handling).
     */
    protected abstract void truncateAll(Connection connection, List<String> tables) throws SQLException;

    /**
     * Runs warmup fills, then {@link #MEASURED_FILLS} timed fills, printing per-iteration and
     * best/mean throughput. The container must already be started with its schema applied.
     */
    protected void runBenchmark(String label, JdbcDatabaseContainer<?> container, DatabaseConfiguration configuration) throws SQLException {
        configuration = applyBulk(configuration);
        try (HikariDataSource dataSource = dataSource(container);
             Connection connection = dataSource.getConnection()) {

            List<String> tables = tableNames(connection);

            // warmup primes the JIT, connection pool and DB caches; not timed
            for (int i = 0; i < WARMUP_FILLS; i++) {
                truncateAll(connection, tables);
                fill(dataSource, connection, configuration);
            }

            // the dataset is deterministic, so the row total is the same for every iteration
            long totalRows = countRows(connection, tables);

            long bestNanos = Long.MAX_VALUE;
            long sumNanos = 0;
            for (int i = 0; i < MEASURED_FILLS; i++) {
                truncateAll(connection, tables);
                long start = System.nanoTime();
                fill(dataSource, connection, configuration);
                long elapsed = System.nanoTime() - start;
                bestNanos = Math.min(bestNanos, elapsed);
                sumNanos += elapsed;
                report(label, "iteration " + (i + 1), elapsed, totalRows);
            }

            report(label, "best", bestNanos, totalRows);
            report(label, "mean", sumNanos / MEASURED_FILLS, totalRows);
        }
    }

    /**
     * Runs one full fill. With {@code bench.threads == 1} (the default) this is the sequential,
     * single-connection path; with more threads it is the {@link javax.sql.DataSource}-based
     * parallel path, filling independent tables concurrently — the issue #447 headline optimization.
     */
    /**
     * Returns a copy of {@code configuration} with {@link BulkLoadStrategy#unorderedBulk()} applied when
     * {@code -Dbench.bulk=true}, otherwise the configuration unchanged. Rebuilds the record so the bulk
     * flag is honored by every benchmark fixture without per-test wiring.
     */
    private static DatabaseConfiguration applyBulk(DatabaseConfiguration configuration) {
        if (!BULK) {
            return configuration;
        }
        return new DatabaseConfiguration(
                configuration.batchSize(), configuration.defaultRowCount(), configuration.databaseSupport(),
                configuration.tableConfigurations(), configuration.generatorRegistry(), configuration.seed(),
                configuration.commitStrategy(), BulkLoadStrategy.unorderedBulk());
    }

    private static void fill(HikariDataSource dataSource, Connection connection, DatabaseConfiguration configuration) throws SQLException {
        if (THREADS > 1) {
            new DatabaseFiller.Builder(dataSource, configuration).threads(THREADS).build().fill();
        } else {
            new DatabaseFiller.Builder(connection, configuration).build().fill();
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        Database database = DatabaseUtils.getMetadata(connection);
        for (Table table : database.tables()) {
            names.add(table.name());
        }
        return names;
    }

    private static long countRows(Connection connection, List<String> tables) throws SQLException {
        long total = 0;
        try (Statement statement = connection.createStatement()) {
            for (String table : tables) {
                try (ResultSet resultSet = statement.executeQuery("select count(*) from " + table)) {
                    resultSet.next();
                    total += resultSet.getLong(1);
                }
            }
        }
        return total;
    }

    private static void report(String label, String phase, long nanos, long rows) {
        double seconds = nanos / 1_000_000_000.0;
        double rowsPerSecond = seconds > 0 ? rows / seconds : 0;
        System.out.printf(Locale.ROOT, "[bench] %-16s %-13s %,12d rows  %8.3f s  %,14.0f rows/s%n",
                label, phase, rows, seconds, rowsPerSecond);
    }
}
