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
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;

class TableFillerTest {

    @Test
    void fill() {

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName("localhost");
        ds.setPortNumber(26257);
        ds.setDatabaseName("bloviate");
        ds.setUser("root");
        ds.setPassword(null);
        ds.setReWriteBatchedInserts(true);
        ds.setApplicationName("FillTest");

        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            //Creating a reader object
            Reader reader = new BufferedReader(new FileReader("/Users/tv/dev/projects/bloviate/src/test/resources/drop_tables.sql"));
            //Running the script
            sr.runScript(reader);
        } catch (FileNotFoundException | SQLException e) {
            e.printStackTrace();
        }

        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            //Creating a reader object
            Reader reader = new BufferedReader(new FileReader("/Users/tv/dev/projects/bloviate/src/test/resources/create_tables.sql"));
            //Running the script
            sr.runScript(reader);
        } catch (FileNotFoundException | SQLException e) {
            e.printStackTrace();
        }


        try (Connection connection = ds.getConnection()) {
            // todo interval, collate, jsonb
            //new TableFiller.Builder(connection, "array_table").build().fill(); //docs no id
            new TableFiller.Builder(connection, "bit_table").build().fill(); //docs no id, cols xyz
            new TableFiller.Builder(connection, "bool_table").build().fill(); //docs int id
            //new TableFiller.Builder(connection, "bytes_table").build().fill(); //docs int id, no create with alias
            new TableFiller.Builder(connection, "date_table").build().fill(); //docs date as primary key?
            new TableFiller.Builder(connection, "decimal_table").build().fill(); //docs decimal as primary key?
            new TableFiller.Builder(connection, "float_table").build().fill(); //docs float as primary key?
            new TableFiller.Builder(connection, "inet_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, "int_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, "string_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, "time_table").build().fill(); //docs inet as primary key?
            new TableFiller.Builder(connection, "timestamp_table").build().fill(); //docs inet as primary key?
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }
}