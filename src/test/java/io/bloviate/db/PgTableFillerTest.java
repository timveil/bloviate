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

import org.apache.ibatis.jdbc.ScriptRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;

class PgTableFillerTest {

    private final PGSimpleDataSource ds = new PGSimpleDataSource();

    @BeforeEach
    void setUp() throws IOException, SQLException {

        ds.setServerNames(new String[]{"localhost"});
        ds.setPortNumbers(new int[]{5432});
        ds.setDatabaseName("bloviate");
        ds.setUser("admin");
        ds.setPassword("password");
        ds.setReWriteBatchedInserts(true);
        ds.setApplicationName("FillTest");

        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            //Creating a reader object
            try (InputStream is = getClass().getResourceAsStream("/pg_drop_tables.sql");
                 Reader reader = new InputStreamReader(is)) {
                //Running the script
                sr.runScript(reader);
            }
        }

        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            //Creating a reader object
            try (InputStream is = getClass().getResourceAsStream("/pg_create_tables.sql");
                 Reader reader = new InputStreamReader(is)) {
                //Running the script
                sr.runScript(reader);
            }
        }
    }

    @Test
    void fill() {
        try (Connection connection = ds.getConnection()) {
            //new TableFiller.Builder(connection, "array_table").build().fill();

            // https://github.com/pgjdbc/pgjdbc/issues/908
            // column "a" is of type bit but expression is of type character varying
            //new TableFiller.Builder(connection, "bit_table").build().fill();

            // column "a" is of type boolean but expression is of type character varying
            //new TableFiller.Builder(connection, "bool_table").build().fill();
            new TableFiller.Builder(connection, "bytes_table").build().fill();
            new TableFiller.Builder(connection, "date_table").build().fill();
            new TableFiller.Builder(connection, "decimal_table").build().fill();
            new TableFiller.Builder(connection, "float_table").build().fill();

            // column "a" is of type inet but expression is of type character varying
            //new TableFiller.Builder(connection, "inet_table").build().fill();

            // column "a" is of type interval but expression is of type character varying
            //new TableFiller.Builder(connection, "interval_table").build().fill();
            new TableFiller.Builder(connection, "int_table").build().fill();
            new TableFiller.Builder(connection, "string_table").build().fill();
            new TableFiller.Builder(connection, "time_table").build().fill();
            new TableFiller.Builder(connection, "timestamp_table").build().fill();
            new TableFiller.Builder(connection, "jsonb_table").build().fill();
            new TableFiller.Builder(connection, "identity_table").build().fill();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}