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

import io.bloviate.ext.DuckDBSupport;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Issue #451: DuckDB. Unlike every other database the engine supports, DuckDB is embedded and
 * in-process, so this is a full type-coverage fill against a real database with <em>no Docker</em>
 * — the same standing as {@link H2FillerTest} and the SQLite tests.
 *
 * <p>{@code special_types} is the point of the exercise: DuckDB reports its unsigned integers as the
 * next signed JDBC type up, and puts UUID, JSON, the 128-bit integers, the sub-second timestamps and
 * ENUM behind {@code OTHER}. Every column here is {@code NOT NULL} and DuckDB rejects an out-of-range
 * conversion, so a wrong generator is a failed insert rather than a quiet mismatch.
 */
class DuckDBFillerTest extends BaseEmbeddedTest {

    private static final String URL = "jdbc:duckdb:";

    private static final int ROWS = 5;

    @Test
    void fillTestTables() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, ROWS, new DuckDBSupport(), new HashSet<>());

        fillDatabase(URL, "create_tables.duckdb.sql", configuration, connection -> {
            assertRowCount(connection, "standard_table", ROWS);
            assertRowCount(connection, "special_types", ROWS);
            assertRowCount(connection, "parents", ROWS);
            assertRowCount(connection, "children", ROWS);

            // the unsigned columns would have been filled from a widened signed range
            assertNoRows(connection, "select 1 from special_types where c_utinyint < 0 or c_usmallint < 0 "
                    + "or c_uinteger < 0 or c_ubigint < 0 or c_uhugeint < 0");
            // the enum's labels, not an arbitrary string
            assertNoRows(connection, "select 1 from special_types where c_enum is null");
            // foreign keys are enforced by DuckDB, so this cannot pass unless the values line up
            assertNoRows(connection, "select 1 from children c where c.parent_id not in (select id from parents)");
        });
    }

    private static void assertNoRows(java.sql.Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from (" + sql + ")")) {
            resultSet.next();
            assertEquals(0, resultSet.getLong(1), sql);
        }
    }
}
