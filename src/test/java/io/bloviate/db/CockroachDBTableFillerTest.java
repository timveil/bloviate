/*
 * Copyright 2020 Tim Veil
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

import io.bloviate.ext.CockroachDBSupport;
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

@Testcontainers
class CockroachDBTableFillerTest extends BaseDatabaseTestCase {

    @Container
    private static final CockroachContainer database = new CockroachContainer("cockroachdb/cockroach:latest")
            .withUrlParam("rewriteBatchedInserts", "true")
            .withInitScript("create_tables.cockroachdb.sql")
            .withCommand("start-single-node --insecure --store=type=mem,size=.75");

    private static DataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        dataSource = getDataSource(database);
    }

    @Test
    void fillArray() throws SQLException {
        fill("array_table");
    }

    @Test
    void fillBit() throws SQLException {
        fill("bit_table");
    }

    @Test
    void fillBool() throws SQLException {
        fill("bool_table");
    }

    @Test
    void fillBytes() throws SQLException {
        fill("bytes_table");
    }

    @Test
    void fillDate() throws SQLException {
        fill("date_table");
    }

    @Test
    void fillDecimal() throws SQLException {
        fill("decimal_table");
    }

    @Test
    void fillFloat() throws SQLException {
        fill("float_table");
    }

    @Test
    void fillInet() throws SQLException {
        fill("inet_table");
    }

    @Test
    void fillInterval() throws SQLException {
        fill("interval_table");
    }

    @Test
    void fillInt() throws SQLException {
        fill("int_table");
    }

    @Test
    void fillString() throws SQLException {
        fill("string_table");
    }

    @Test
    void fillTime() throws SQLException {
        fill("time_table");
    }

    @Test
    void fillTimestamp() throws SQLException {
        fill("timestamp_table");
    }

    @Test
    void fillJsonb() throws SQLException {
        fill("jsonb_table");
    }

    @Test
    void fillIdentity() throws SQLException {
        fill("identity_table");
    }

    private void fill(String tableName) throws SQLException {

        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        tableConfigurations.add(new TableConfiguration(tableName, 10));

        DatabaseConfiguration config = new DatabaseConfiguration(128, 5, new CockroachDBSupport(), tableConfigurations);


        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseUtils.getMetadata(connection);

            Table table = database.getTable(tableName);

            new TableFiller.Builder(connection, database, config).table(table).build().fill();
        }
    }
}