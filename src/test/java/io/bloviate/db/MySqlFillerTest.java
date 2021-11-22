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

import io.bloviate.ext.MySQLSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

class MySqlFillerTest extends BaseDatabaseTestCase {

    @Test
    void fillDatabase() throws SQLException {

        try (MySQLContainer<?> database = new MySQLContainer<>("mysql:latest")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedStatements", "true")
                .withInitScript("create_tpcc.mysql.sql")) {
            database.start();

            DataSource dataSource = getDataSource(database);
            try (Connection connection = dataSource.getConnection()) {
                new DatabaseFiller.Builder(connection).databaseSupport(new MySQLSupport()).build().fill();
            }
        }
    }
}