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

import java.sql.SQLException;
import java.util.HashSet;

class H2FillerTest extends BaseEmbeddedTest {

    // a fresh in-memory database per test, kept alive for the connection's lifetime
    private static final String URL = "jdbc:h2:mem:bloviate_%s;DB_CLOSE_DELAY=-1";

    @Test
    void fillTestTables() throws SQLException {
        // standard_table covers the standard types; special_types covers H2's UUID and JSON.
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new H2Support(), new HashSet<>());
        fillDatabase(URL.formatted("types"), "create_tables.h2.sql", configuration, connection -> {
            assertRowCount(connection, "standard_table", 5);
            assertRowCount(connection, "special_types", 5);
        });
    }
}
