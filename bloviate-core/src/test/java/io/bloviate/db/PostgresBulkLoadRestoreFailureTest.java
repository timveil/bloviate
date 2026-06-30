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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.BulkLoadHandle;
import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the bulk-load failure path: when re-enabling constraints throws, the borrowed connection is
 * still in its constraint-disabled session state ({@code session_replication_role = replica}) and must
 * not be returned to the pool, or whatever borrows it next would silently skip foreign-key enforcement
 * (issue #526, H2). The fix aborts the physical connection so the pool discards it.
 *
 * <p>The pool is capped at a single connection, so the connection re-borrowed after the failed fill is
 * necessarily the same physical one the fill poisoned — had it been returned rather than aborted, the
 * assertion below would observe {@code replica}.
 */
class PostgresBulkLoadRestoreFailureTest extends BaseDatabaseTestCase {

    /** A {@link PostgresSupport} whose constraint restore always fails, simulating a transient error. */
    private static final class RestoreFailingSupport extends PostgresSupport {
        @Override
        public void enableConstraints(Connection connection, Database database, BulkLoadHandle handle) throws SQLException {
            throw new SQLException("simulated failure re-enabling constraints");
        }
    }

    @Test
    void abortsConnectionWhenConstraintRestoreFails() throws SQLException {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")) {

            database.start();

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(database.getJdbcUrl());
            hikariConfig.setUsername(database.getUsername());
            hikariConfig.setPassword(database.getPassword());
            hikariConfig.setDriverClassName(database.getDriverClassName());
            // a single connection: the one re-borrowed after the fill is the very one the fill used
            hikariConfig.setMaximumPoolSize(1);

            try (HikariDataSource dataSource = new HikariDataSource(hikariConfig)) {

                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("create table widget (id integer primary key, label varchar(32))");
                }

                DatabaseConfiguration configuration = new DatabaseConfiguration(
                        16, 10, new RestoreFailingSupport(), null, 42L, null, BulkLoadStrategy.unorderedBulk());

                // the restore failure must surface, not be swallowed
                assertThrows(SQLException.class, () ->
                        new DatabaseFiller.Builder(dataSource, configuration).threads(2).build().fill());

                // the poisoned connection must have been discarded: a freshly borrowed connection from
                // the size-1 pool reports the restored role, not the bulk-load 'replica' state
                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement();
                     ResultSet resultSet = statement.executeQuery("show session_replication_role")) {
                    resultSet.next();
                    assertEquals("origin", resultSet.getString(1),
                            "a connection whose constraint restore failed must not be returned to the pool");
                }
            }
        }
    }
}
