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

import io.bloviate.ext.SQLiteSupport;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;

class SQLiteFillerTest extends BaseEmbeddedTest {

    // a single shared in-memory database for the connection's lifetime
    private static final String URL = "jdbc:sqlite::memory:";

    @Test
    void fillTestTables() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new SQLiteSupport(), new HashSet<>());
        fillDatabase(URL, "create_tables.sqlite.sql", configuration, connection ->
                assertRowCount(connection, "standard_table", 5));
    }
}
