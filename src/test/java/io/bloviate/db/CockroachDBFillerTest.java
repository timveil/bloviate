/*
 * Copyright 2020 Tim Veil
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

import io.bloviate.ext.CockroachDBSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

class CockroachDBFillerTest extends BaseDatabaseTestCase {

    @Test
    void fillDatabase() throws SQLException {

        try (CockroachContainer database = new CockroachContainer("cockroachdb/cockroach:latest")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withInitScript("create_tpcc.cockroachdb.sql")
                .withCommand("start-single-node --insecure --store=type=mem,size=.75")) {

            database.start();

            DataSource dataSource = getDataSource(database);

            try (Connection connection = dataSource.getConnection()) {
                new DatabaseFiller.Builder(connection).databaseSupport(new CockroachDBSupport()).rows(10).build().fill();
            }
        }
    }
}