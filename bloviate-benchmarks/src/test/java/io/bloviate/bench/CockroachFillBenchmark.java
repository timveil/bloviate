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
import io.bloviate.ext.CockroachDBSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * End-to-end fill throughput on CockroachDB, driving the realistic TPC-C schema. Opt-in — run with
 * {@code -Pbench}.
 */
@Tag("benchmark")
class CockroachFillBenchmark extends AbstractFillBenchmark {

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
                BATCH_SIZE, 0, new CockroachDBSupport(), tpccTables(), SEED);

        try (CockroachContainer database = new CockroachContainer("cockroachdb/cockroach:latest")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withInitScript("create_tpcc.cockroachdb.sql")
                .withCommand("start-single-node --insecure --store=type=mem,size=.75")) {
            database.start();
            runBenchmark("cockroach/tpcc", database, configuration);
        }
    }
}
