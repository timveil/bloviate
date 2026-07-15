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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Required reference parameters on the public builders fail fast with a descriptive message
 * instead of surfacing as a distant NPE deep inside {@code fill()}.
 */
class BuilderValidationTest {

    @Test
    void databaseFillerBuilderRejectsNulls() {
        DatabaseConfiguration configuration = new DatabaseConfiguration(10, 10, new H2Support(), null);

        assertThrows(NullPointerException.class,
                () -> new DatabaseFiller.Builder((Connection) null, configuration));
        assertThrows(NullPointerException.class,
                () -> new DatabaseFiller.Builder((DataSource) null, configuration));
    }

    @Test
    void databaseFillerBuilderRejectsNullConfiguration() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:builder_validation")) {
            assertThrows(NullPointerException.class,
                    () -> new DatabaseFiller.Builder(connection, null));
        }
    }

    @Test
    void tableFillerBuildRequiresTable() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(10, 10, new H2Support(), null);
        Database database = new Database("h2", "2", null, null, List.of());

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:builder_validation")) {
            TableFiller.Builder builder = new TableFiller.Builder(connection, database, configuration);
            assertThrows(IllegalStateException.class, builder::build);
        }
    }
}
