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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

    /**
     * Executes a semicolon-delimited SQL script loaded from the test classpath. Line comments
     * ({@code --}) are stripped; statements are split on {@code ;} at end of line.
     */
    protected static void runScript(Connection connection, String resource) throws SQLException {
        String sql = readResource(resource);
        try (Statement statement = connection.createStatement()) {
            for (String stmt : sql.split(";\\s*\\n")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    private static String readResource(String resource) {
        try (InputStream in = BaseEmbeddedTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("init script not found on classpath: " + resource);
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int comment = line.indexOf("--");
                    if (comment >= 0) {
                        line = line.substring(0, comment);
                    }
                    builder.append(line).append('\n');
                }
            }
            return builder.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
