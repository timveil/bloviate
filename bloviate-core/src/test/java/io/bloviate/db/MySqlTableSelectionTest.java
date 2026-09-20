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

import io.bloviate.ext.MySQLSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog selection against a real MySQL, where a database is a catalog: the fill must reach the other
 * database through the <em>connection</em> (MySQL reports no schema, so the generated inserts are
 * unqualified), and {@code schema(...)} must fail loudly instead of silently filling the wrong place.
 */
class MySqlTableSelectionTest extends BaseDatabaseTestCase {

    private static final int ROWS = 25;

    private static final String SCHEMA = """
            create database other;
            create table bloviate.detail (id int primary key, amount int);
            create table other.detail (id int primary key, amount int);
            create table other.order_stats (n bigint);
            """;

    @Test
    void catalogSelectionFillsAnotherDatabaseOnEveryPathAndRestoresTheConnection() throws SQLException {
        try (MySQLContainer<?> database = new MySQLContainer<>(TestImages.MYSQL)
                .withConfigurationOverride("mysql-conf")
                .withDatabaseName("bloviate")
                .withUrlParam("rewriteBatchedStatements", "true")) {
            database.start();

            DatabaseConfiguration configuration = new DatabaseConfiguration(16, ROWS, new MySQLSupport(), null, 42L);
            String rollup = "insert into order_stats select count(*) from detail";

            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), "root", database.getPassword())) {
                SqlScriptRunner.run(connection, SqlScript.inline("schema", SCHEMA));

                // single connection sitting on `bloviate`
                new DatabaseFiller.Builder(connection, configuration).catalog("other").excludeTables("order_stats")
                        .after(SqlScript.inline("rollup", rollup)).build().fill();

                assertRowCount(connection, "bloviate.detail", 0);
                assertRowCount(connection, "other.detail", ROWS);
                assertCount(connection, "select n from other.order_stats", ROWS);
                assertEquals("bloviate", connection.getCatalog(), "the caller's catalog must be restored");

                // schema selection is not something MySQL can do
                DatabaseFiller schemaFiller = new DatabaseFiller.Builder(connection, configuration).schema("other").build();
                SQLException e = assertThrows(SQLException.class, schemaFiller::fill);
                assertTrue(e.getMessage().contains("cannot select schema [other]"), e.getMessage());
                assertTrue(e.getMessage().contains("catalog"), e.getMessage());
                assertEquals("bloviate", connection.getCatalog());

                SqlScriptRunner.run(connection, SqlScript.inline("reset", "delete from other.detail; delete from other.order_stats"));
            }

            // a sequential DataSource and a threaded one, both on unwrapped physical connections
            for (int threads : new int[]{1, 3}) {
                try (TrackingDataSource dataSource = new TrackingDataSource(database.getJdbcUrl(), "root", database.getPassword(), threads);
                     Connection observer = DriverManager.getConnection(database.getJdbcUrl(), "root", database.getPassword())) {
                    new DatabaseFiller.Builder(dataSource, configuration).threads(threads)
                            .catalog("other").excludeTables("order_stats")
                            .after(SqlScript.inline("rollup", rollup)).build().fill();

                    assertRowCount(observer, "bloviate.detail", 0);
                    assertRowCount(observer, "other.detail", ROWS);
                    assertCount(observer, "select n from other.order_stats", ROWS);
                    for (Connection physical : dataSource.physicalConnections()) {
                        assertEquals("bloviate", physical.getCatalog(), "threads=" + threads);
                    }
                    SqlScriptRunner.run(observer, SqlScript.inline("reset", "delete from other.detail; delete from other.order_stats"));
                }
            }
        }
    }
}
