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

package io.bloviate.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloviateContainersTest {

    @Test
    void fillsStartedContainer() throws SQLException {
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withInitScript("widget.sql")) {

            container.start();

            BloviateContainers.forContainer(container)
                    .rows(25)
                    .seed(7)
                    .fill();

            try (Connection connection = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select count(*) from widget")) {

                resultSet.next();
                assertEquals(25L, resultSet.getLong(1));
            }
        }
    }
}
