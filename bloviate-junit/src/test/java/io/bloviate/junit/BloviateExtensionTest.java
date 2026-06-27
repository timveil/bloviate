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

package io.bloviate.junit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end test that {@link FillDatabase}/{@link BloviateExtension} fill a real database. A
 * single test method keeps the assertion order-independent (the extension appends rows on every
 * {@code beforeEach} rather than resetting the schema).
 */
@FillDatabase(rows = BloviateExtensionTest.ROWS, seed = 7)
class BloviateExtensionTest {

    static final long ROWS = 25;

    private static PostgreSQLContainer<?> container;

    // a raw Connection source: the extension fills through it and leaves it open for the test
    @FillSource
    static Connection connection;

    @BeforeAll
    static void startDatabase() throws SQLException {
        container = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withInitScript("widget.sql");
        container.start();
        connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @AfterAll
    static void stopDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void fillsConfiguredRowCount() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from widget")) {
            resultSet.next();
            assertEquals(ROWS, resultSet.getLong(1));
        }
    }
}
