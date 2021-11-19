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

class ManualTableFillerTest {

    private final PGSimpleDataSource ds = new PGSimpleDataSource();

    @BeforeEach
    void setUp() throws SQLException, IOException {

        ds.setServerNames(new String[]{"localhost"});
        ds.setPortNumbers(new int[]{26257});
        ds.setDatabaseName("bloviate");
        ds.setUser("root");
        ds.setPassword(null);
        ds.setReWriteBatchedInserts(true);
        ds.setApplicationName("ManualTableFillerTest");

        Database db = DatabaseUtils.getMetadata(ds);

        if (db.getTables() != null && !db.getTables().isEmpty()) {
            for (Table table : db.getTables()) {
                try (Connection connection = ds.getConnection();
                     Statement stmt = connection.createStatement()) {
                    stmt.execute(String.format("drop table %s cascade", table.getName()));
                }
            }
        }

        try (Connection connection = ds.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            try (InputStream is = getClass().getResourceAsStream("/create_tpcc.cockroachdb.sql")) {
                if (is != null) {
                    try (Reader reader = new InputStreamReader(is)) {
                        sr.runScript(reader);
                    }
                }
            }
        }
    }

    @Test
    void fill() throws SQLException {
        try (Connection connection = ds.getConnection()) {
            Database database = DatabaseUtils.getMetadata(connection);
            CockroachDBSupport support = new CockroachDBSupport();

            new TableFiller.Builder(connection, database, support).table(database.getTable("warehouse")).rows(100).build().fill();
            new TableFiller.Builder(connection, database, support).table(database.getTable("item")).rows(100000).build().fill();
            new TableFiller.Builder(connection, database, support).table(database.getTable("stock")).rows(1000).build().fill();
        }

    }
}