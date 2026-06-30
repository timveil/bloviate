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
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * Verifies the bounded, backpressured task submission on the parallel path (issue #526, M4): when a
 * table is split into far more partitions than there are worker threads, every partition task must
 * still run to completion even though only a small multiple of the pool size is ever in flight at once
 * (see {@code DatabaseFiller#runWithBackpressure}). A dropped or never-submitted task would leave the
 * table short of its configured row count.
 */
class PostgresPartitionBackpressureTest extends BaseDatabaseTestCase {

    private static final long ROWS = 100;
    private static final int PARTITIONS = 16; // >> 2 * threads, so the submission loop must refill
    private static final int THREADS = 2;

    @Test
    void everyPartitionRunsWhenPartitionsFarExceedThreads() throws SQLException {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database)) {

                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("create table widget (id integer)");
                }

                Set<TableConfiguration> tables = Set.of(new TableConfiguration("widget", ROWS, PARTITIONS));
                DatabaseConfiguration configuration = new DatabaseConfiguration(8, 0, new PostgresSupport(), tables);

                new DatabaseFiller.Builder(dataSource, configuration).threads(THREADS).build().fill();

                // all PARTITIONS range tasks ran: the partitions sum back to exactly ROWS
                try (Connection connection = dataSource.getConnection()) {
                    assertRowCount(connection, "widget", ROWS);
                }
            }
        }
    }
}
