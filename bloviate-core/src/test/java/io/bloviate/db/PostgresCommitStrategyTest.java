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

package io.bloviate.db;

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * Verifies issue #467: an explicit {@link CommitStrategy} on the sequential (single-{@code Connection})
 * fill path produces correct data. The engine takes over the transaction (autocommit off, commit at the
 * configured cadence) and must leave a complete, valid dataset.
 */
class PostgresCommitStrategyTest extends BaseDatabaseTestCase {

    private static final int W = 1;
    private static final int I = 100;
    private static final int D = 10;
    private static final int C = 20;
    private static final int MIN_LINES = 5;
    private static final int MAX_LINES = 15;
    private static final int NEW_ORDERS = 8;

    @Test
    void perTableAndEveryNBatchesCommitStrategiesFillCorrectly() throws SQLException {
        Set<TableConfiguration> tables = TPCCConfiguration.build(W, I, D, C, MIN_LINES, MAX_LINES, NEW_ORDERS);

        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_tpcc.postgres.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database)) {

                // commit once per table on the sequential path (autocommit off, single commit per table)
                fillAndVerify(dataSource, new DatabaseConfiguration(64, 0, new PostgresSupport(), tables, 7L, CommitStrategy.perTable()));

                // commit every N batches: bounds the open transaction while filling
                fillAndVerify(dataSource, new DatabaseConfiguration(64, 0, new PostgresSupport(), tables, 7L, CommitStrategy.everyNBatches(3)));
            }
        }
    }

    private void fillAndVerify(HikariDataSource dataSource, DatabaseConfiguration configuration) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration).build().fill();

            assertRowCount(connection, "warehouse", W);
            assertRowCount(connection, "stock", (long) W * I);
            assertRowCount(connection, "customer", (long) W * D * C);
            assertTpccColumnFidelity(connection, C, MIN_LINES, MAX_LINES, NEW_ORDERS);

            truncateAll(connection);
        }
    }

    private static void truncateAll(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE warehouse, item, stock, district, customer, history, open_order, new_order, order_line CASCADE");
        }
    }
}
