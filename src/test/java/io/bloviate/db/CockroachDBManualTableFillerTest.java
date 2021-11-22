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
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Testcontainers
class CockroachDBManualTableFillerTest extends BaseDatabaseTestCase {

    @Container
    private static final CockroachContainer database = new CockroachContainer("cockroachdb/cockroach:latest")
            .withUrlParam("rewriteBatchedInserts", "true")
            .withInitScript("create_tpcc.cockroachdb.sql")
            .withCommand("start-single-node --insecure --store=type=mem,size=.75");

    private static DataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        dataSource = getDataSource(database);
    }

    @Test
    void fill() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseUtils.getMetadata(connection);

            DatabaseConfiguration config = new DatabaseConfiguration(128, 5, new CockroachDBSupport());

            new TableFiller.Builder(connection, database, config).table(database.getTable("warehouse")).rows(1).build().fill();
            new TableFiller.Builder(connection, database, config).table(database.getTable("item")).rows(2).build().fill();
            new TableFiller.Builder(connection, database, config).table(database.getTable("stock")).rows(3).build().fill();
        }

    }
}