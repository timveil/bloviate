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

package io.bloviate.db.cockroach;

import io.bloviate.db.Database;
import io.bloviate.db.Table;
import io.bloviate.db.TableFiller;
import io.bloviate.ext.CockroachDBSupport;
import io.bloviate.util.DatabaseUtils;
import io.bloviate.util.ScriptRunner;
import org.junit.jupiter.api.BeforeAll;
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

    private static final PGSimpleDataSource ds = new PGSimpleDataSource();

    @BeforeAll
    static void setUp() throws SQLException, IOException {

        ds.setServerNames(new String[]{"localhost"});
        ds.setPortNumbers(new int[]{26257});
        ds.setDatabaseName("bloviate");
        ds.setUser("root");
        ds.setPassword(null);
        ds.setReWriteBatchedInserts(true);
        ds.setApplicationName("TableFillerTest");

        Database db = DatabaseUtils.getMetadata(ds);

        for (Table table : db.getTables()) {
            try (Connection connection = ds.getConnection();
                 Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("drop table %s cascade", table.getName()));
            }
        }


        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            try (InputStream is = TableFillerTest.class.getResourceAsStream("/create_tables.cockroachdb.sql")) {
                if (is != null) {
                    try (Reader reader = new InputStreamReader(is)) {
                        sr.runScript(reader);
                    }
                }
            }
        }
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
        try (Connection connection = ds.getConnection()) {
            Database database = DatabaseUtils.getMetadata(connection);
            Table table = database.getTable(tableName);
            new TableFiller.Builder(connection, database, new CockroachDBSupport()).table(table).build().fill();
        }
    }
}