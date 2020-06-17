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
import java.sql.Statement;

class TableFillerTest {

    private final PGSimpleDataSource ds = new PGSimpleDataSource();

    @BeforeEach
    void setUp() throws SQLException, IOException {

        ds.setServerNames(new String[]{"localhost"});
        ds.setPortNumbers(new int[]{26257});
        ds.setDatabaseName("bloviate");
        ds.setUser("root");
        ds.setPassword(null);
        ds.setReWriteBatchedInserts(true);
        ds.setApplicationName("FillTest");

        Database db = DatabaseUtils.getMetadata(ds);

        for (Table table : db.getTables()) {
            try (Connection connection = ds.getConnection();
                 Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("drop table %s cascade", table.getName()));
            }
        }


        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            //Creating a reader object
            try (InputStream is = getClass().getResourceAsStream("/create_tables.sql");
                 Reader reader = new InputStreamReader(is)) {
                //Running the script
                sr.runScript(reader);
            }
        }
    }

    @Test
    void fill() {
        try (Connection connection = ds.getConnection()) {
            Database database = DatabaseUtils.getMetadata(connection);

            new TableFiller.Builder(connection, database, "array_table").build().fill(); //docs no id
            new TableFiller.Builder(connection, database, "bit_table").build().fill(); //docs no id, cols xyz
            new TableFiller.Builder(connection, database, "bool_table").build().fill(); //docs int id
            new TableFiller.Builder(connection, database, "bytes_table").build().fill(); //docs int id, no create with alias
            new TableFiller.Builder(connection, database, "date_table").build().fill(); //docs date as primary key?
            new TableFiller.Builder(connection, database, "decimal_table").build().fill(); //docs decimal as primary key?
            new TableFiller.Builder(connection, database, "float_table").build().fill(); //docs float as primary key?
            new TableFiller.Builder(connection, database, "inet_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, database, "interval_table").build().fill();
            new TableFiller.Builder(connection, database, "int_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, database, "string_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, database, "time_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, database, "timestamp_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, database, "jsonb_table").build().fill();
            new TableFiller.Builder(connection, database, "identity_table").build().fill();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}