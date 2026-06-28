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

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Verifies issue #479 constraint conformance end-to-end: filling a schema with an enum type and
 * CHECK constraints produces only values that satisfy them. The enum column is the strongest signal —
 * without constraint awareness, the PostgreSQL support throws on its unknown {@code OTHER} type, so a
 * successful fill proves the enum's allowed values were read and used.
 */
class PostgresConstraintFillTest extends BaseDatabaseTestCase {

    @Test
    void generatedValuesSatisfyChecksAndEnums() throws SQLException {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(256, 1_000, new PostgresSupport(), null, 42L);

        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("bloviate")
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_constraints.postgres.sql")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database);
                 Connection connection = dataSource.getConnection()) {

                // the fill only completes if the enum and CHECK constraints are honored
                new DatabaseFiller.Builder(connection, configuration).build().fill();

                assertRowCount(connection, "constrained", 1_000);

                // every column conforms to its constraint
                assertCount(connection, "select count(*) from constrained where status not in ('NEW','PAID','SHIPPED','CANCELLED')", 0);
                assertCount(connection, "select count(*) from constrained where rating < 1 or rating > 5", 0);
                assertCount(connection, "select count(*) from constrained where priority not in (1,2,3)", 0);
                assertCount(connection, "select count(*) from constrained where grade not in ('A','B','C','D','F')", 0);
                assertCount(connection, "select count(*) from constrained where amount < 0 or amount > 9999.99", 0);
                assertCount(connection, "select count(*) from constrained where score < 0 or score > 100", 0);

                // and the generators actually vary across the allowed space (not a single constant)
                assertMoreThanOne(connection, "select count(distinct status) from constrained");
                assertMoreThanOne(connection, "select count(distinct rating) from constrained");
                assertMoreThanOne(connection, "select count(distinct grade) from constrained");
            }
        }
    }

    private static void assertMoreThanOne(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement(); var rs = statement.executeQuery(query)) {
            rs.next();
            if (rs.getLong(1) <= 1) {
                throw new AssertionError("expected variety but got " + rs.getLong(1) + " distinct for: " + query);
            }
        }
    }
}
