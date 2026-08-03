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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Base class for embedded, in-process database tests (H2, SQLite) that need no Docker. Subclasses
 * supply a JDBC URL (and optional credentials); this class opens a single connection, runs an init
 * script loaded from the test classpath, fills the database, and invokes an optional verifier.
 */
public abstract class BaseEmbeddedTest extends BaseDatabaseTestCase {

    protected void fillDatabase(String jdbcUrl, String initScript, DatabaseConfiguration configuration) throws SQLException {
        fillDatabase(jdbcUrl, null, null, initScript, configuration, null);
    }

    protected void fillDatabase(String jdbcUrl, String initScript, DatabaseConfiguration configuration, Verifier verifier) throws SQLException {
        fillDatabase(jdbcUrl, null, null, initScript, configuration, verifier);
    }

    protected void fillDatabase(String jdbcUrl, String user, String password, String initScript,
                                DatabaseConfiguration configuration, Verifier verifier) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            runScript(connection, initScript);

            new DatabaseFiller.Builder(connection, configuration).build().fill();

            if (verifier != null) {
                verifier.verify(connection);
            }
        }
    }

}
