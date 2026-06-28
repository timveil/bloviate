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

import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.db.DatabaseFiller;
import io.bloviate.db.TableConfiguration;
import io.bloviate.ext.DatabaseSupport;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;

/**
 * Fills a started {@link JdbcDatabaseContainer} with Bloviate-generated data — the public form of
 * the pattern Bloviate uses in its own tests.
 *
 * <p>The container must already be running and its schema created (for example via
 * {@code withInitScript(...)}); Bloviate fills the tables it discovers. The
 * {@link DatabaseSupport} is auto-detected from the connection unless one is supplied.
 *
 * <pre>{@code
 * try (var postgres = new PostgreSQLContainer<>("postgres:18-alpine").withInitScript("schema.sql")) {
 *     postgres.start();
 *     BloviateContainers.forContainer(postgres)
 *         .rows(500)
 *         .seed(42)
 *         .fill();
 * }
 * }</pre>
 */
public final class BloviateContainers {

    private BloviateContainers() {
    }

    /**
     * Begins configuring a fill for the given started container.
     *
     * @param container a started JDBC container whose schema already exists
     * @return a builder to configure and run the fill
     */
    public static Builder forContainer(JdbcDatabaseContainer<?> container) {
        return new Builder(container);
    }

    /**
     * Configures and runs a fill against a {@link JdbcDatabaseContainer}.
     */
    public static final class Builder {

        private final JdbcDatabaseContainer<?> container;
        private int batchSize = 1000;
        private long rows = 100L;
        private long seed = 0L;
        private DatabaseSupport databaseSupport;
        private Set<TableConfiguration> tableConfigurations;

        private Builder(JdbcDatabaseContainer<?> container) {
            this.container = container;
        }

        /**
         * Sets the JDBC batch size for INSERTs.
         *
         * @param batchSize the JDBC batch size for INSERTs (default 1000)
         * @return this builder
         */
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Sets the default number of rows to generate per table.
         *
         * @param rows the default number of rows to generate per table (default 100)
         * @return this builder
         */
        public Builder rows(long rows) {
            this.rows = rows;
            return this;
        }

        /**
         * Sets the base seed for reproducible generation.
         *
         * @param seed the base seed for reproducible generation (default 0); vary it for a
         *             different but reproducible dataset
         * @return this builder
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Overrides the auto-detected {@link DatabaseSupport}.
         *
         * @param databaseSupport the support to use, or null to auto-detect from the connection
         * @return this builder
         */
        public Builder databaseSupport(DatabaseSupport databaseSupport) {
            this.databaseSupport = databaseSupport;
            return this;
        }

        /**
         * Sets optional per-table overrides.
         *
         * @param tableConfigurations optional per-table overrides (row counts, column generators)
         * @return this builder
         */
        public Builder tableConfigurations(Set<TableConfiguration> tableConfigurations) {
            this.tableConfigurations = tableConfigurations;
            return this;
        }

        /**
         * Opens a connection to the container and fills its schema.
         *
         * @throws SQLException if a database access error occurs
         */
        public void fill() throws SQLException {
            try (Connection connection = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword())) {

                DatabaseSupport support = databaseSupport != null
                        ? databaseSupport
                        : DatabaseSupport.forConnection(connection);

                DatabaseConfiguration configuration =
                        new DatabaseConfiguration(batchSize, rows, support, tableConfigurations, seed);

                new DatabaseFiller.Builder(connection, configuration).build().fill();
            }
        }
    }
}
