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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseFiller implements Fillable {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final int rows;
    private final int batchSize;

    @Override
    public void fill() throws SQLException {

        DatabaseMetaData databaseMetaData = connection.getMetaData();

        String catalog = connection.getCatalog();
        String schema = connection.getSchema();

        try (ResultSet tables = databaseMetaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                new TableFiller.Builder(connection, tableName)
                        .catalog(catalog)
                        .schemaPattern(schema)
                        .batchSize(batchSize)
                        .rows(rows)
                        .build().fill();
            }
        }

    }

    public static class Builder {

        private final Connection connection;

        private int rows = 1000;
        private int batchSize = 128;

        public Builder(Connection connection) {
            this.connection = connection;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public DatabaseFiller build() {
            return new DatabaseFiller(this);
        }
    }

    private DatabaseFiller(Builder builder) {
        this.connection = builder.connection;
        this.rows = builder.rows;
        this.batchSize = builder.batchSize;
    }
}
