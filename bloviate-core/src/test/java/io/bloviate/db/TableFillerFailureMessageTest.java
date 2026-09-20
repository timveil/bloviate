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

import io.bloviate.ext.H2Support;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failed insert must say which table failed (a driver's own message rarely does, and on a parallel
 * fill the caller cannot otherwise tell), without losing the driver's message, SQL state or cause.
 */
class TableFillerFailureMessageTest {

    @Test
    void aFailedInsertNamesTheTableAndKeepsTheDriversFailureAsTheCause() throws SQLException {
        String url = "jdbc:h2:mem:failure_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", "create table widget (id int primary key, label varchar(20));"));
            DatabaseConfiguration configuration = new DatabaseConfiguration(8, 5, new H2Support(), null, 1L);
            new DatabaseFiller.Builder(connection, configuration).build().fill();

            // not idempotent: the same seed generates the same keys, which now collide
            SQLException failure = assertThrows(SQLException.class,
                    () -> new DatabaseFiller.Builder(connection, configuration).build().fill());

            assertTrue(failure.getMessage().startsWith("failed to fill table [WIDGET]: "), failure.getMessage());
            assertNotNull(failure.getCause(), "the driver's exception is the cause");
            assertTrue(failure.getMessage().contains(failure.getCause().getMessage()), "the driver's message is kept");
            assertEquals(((SQLException) failure.getCause()).getSQLState(), failure.getSQLState());
            assertEquals(((SQLException) failure.getCause()).getErrorCode(), failure.getErrorCode());
        }
    }
}
