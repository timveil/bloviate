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
import io.bloviate.ext.MySQLSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * End-to-end fill throughput on MySQL, driving the realistic TPC-C schema. Opt-in — run with
 * {@code -Pbench}.
 */
@Tag("benchmark")
class MySqlFillBenchmark extends AbstractFillBenchmark {

    @Override
    protected void truncateAll(Connection connection, List<String> tables) throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        // MySQL refuses TRUNCATE on a table referenced by a foreign key, so disable the checks
        // for the duration of the reset
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                statement.execute("TRUNCATE TABLE " + table);
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void tpcc() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(
                BATCH_SIZE, 0, new MySQLSupport(), tpccTables(), SEED);

        try (MySQLContainer<?> database = new MySQLContainer<>("mysql:9.7")
                .withConfigurationOverride("mysql-conf")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedStatements", "true")
                .withInitScript("create_tpcc.mysql.sql")) {
            database.start();
            runBenchmark("mysql/tpcc", database, configuration);
        }
    }
}
