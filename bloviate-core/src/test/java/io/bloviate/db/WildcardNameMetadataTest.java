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
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for names that contain a LIKE wildcard, against in-memory H2 (no Docker).
 *
 * <p>JDBC's {@code getTables} and {@code getColumns} take the schema and table name as <em>patterns</em>,
 * so before these names were escaped a schema {@code tenant_1} also matched {@code tenantx1} and a table
 * {@code order_items} also matched {@code orderxitems}: the fill discovered another schema's tables, and
 * one table's column list carried another table's columns.
 */
class WildcardNameMetadataTest {

    private static final int ROWS = 5;

    private static final String SCHEMA = """
            create schema tenant_1;
            create schema tenantx1;
            create table tenant_1.order_items (id int primary key, sku varchar(10));
            create table tenant_1.orderxitems (id int primary key, note varchar(10), extra int);
            create table tenantx1.other_tenant (id int primary key);
            """;

    private String url;

    @BeforeEach
    void freshDatabase() throws SQLException {
        url = "jdbc:h2:mem:wildcard_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", SCHEMA));
        }
    }

    @Test
    void aSchemaWithAnUnderscoreDoesNotDiscoverTheOtherSchemasTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("TENANT_1");

            Database database = DatabaseUtils.getMetadata(connection);

            assertEquals(List.of("ORDERXITEMS", "ORDER_ITEMS"),
                    database.tables().stream().map(Table::name).sorted().toList());
        }
    }

    @Test
    void aTableWithAnUnderscoreDoesNotPickUpTheOtherTablesColumns() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("TENANT_1");

            Database database = DatabaseUtils.getMetadata(connection);

            assertEquals(List.of("ID", "SKU"), columnNames(database, "ORDER_ITEMS"));
            assertEquals(List.of("ID", "NOTE", "EXTRA"), columnNames(database, "ORDERXITEMS"));
        }
    }

    @Test
    void bothTablesFillAndTheOtherSchemaIsLeftAlone() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setSchema("TENANT_1");

            new DatabaseFiller.Builder(connection, new DatabaseConfiguration(16, ROWS, new H2Support(), null, 42L))
                    .build()
                    .fill();

            assertEquals(ROWS, count(connection, "tenant_1.order_items"));
            assertEquals(ROWS, count(connection, "tenant_1.orderxitems"));
            assertEquals(0, count(connection, "tenantx1.other_tenant"));
        }
    }

    @Test
    void selectingTheSchemaByNameIsJustAsNarrow() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            // the connection sits on the other schema; only the selection names TENANT_1
            connection.setSchema("TENANTX1");

            new DatabaseFiller.Builder(connection, new DatabaseConfiguration(16, ROWS, new H2Support(), null, 42L))
                    .schema("TENANT_1")
                    .build()
                    .fill();

            assertEquals(ROWS, count(connection, "tenant_1.order_items"));
            assertEquals(ROWS, count(connection, "tenant_1.orderxitems"));
            assertEquals(0, count(connection, "tenantx1.other_tenant"));
        }
    }

    private static List<String> columnNames(Database database, String table) {
        return database.tables().stream()
                .filter(t -> t.name().equals(table))
                .findFirst()
                .orElseThrow(() -> new AssertionError("table [" + table + "] was not discovered"))
                .columns().stream()
                .map(Column::name)
                .toList();
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
