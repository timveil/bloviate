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

class DatabaseFillerTest {

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
            try (InputStream is = getClass().getResourceAsStream("/create_tpcc.sql");
                 Reader reader = new InputStreamReader(is)) {
                //Running the script
                sr.runScript(reader);
            }
        }
    }

    @Test
    void fill() {
        try (Connection connection = ds.getConnection()) {

            new DatabaseFiller.Builder(connection).build().fill();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}