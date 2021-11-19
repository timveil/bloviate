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

import io.bloviate.ext.DatabaseSupport;
import io.bloviate.gen.DataGenerator;
import io.bloviate.util.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TableFiller implements Fillable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final Database database;
    private final DatabaseSupport databaseSupport;
    private final Table table;
    private final int rows;
    private final int batchSize;

    @Override
    public void fill() throws SQLException {

        // first fill keys then tables;

        String sql = table.insertString();

        logger.debug(sql);

        // case 1 - simple case
        // a - table has no foreign keys, simple primary key is not auto generated
        // b - table has no foreign keys, simple primary key is auto generated
        // c - table has no foreign keys, compound primary key is not auto generated
        // d - table has no foreign keys, compound primary key is auto generated


        Map<Column, DataGenerator<?>> generatorMap = new HashMap<>();

        List<Column> filteredColumns = table.filteredColumns();

        for (Column column : filteredColumns) {

            Random random;

            Column associatedPrimaryKeyColumn = DatabaseUtils.getAssociatedPrimaryKeyColumn(database, table, column);

            if (associatedPrimaryKeyColumn != null) {
                random = new Random(associatedPrimaryKeyColumn.hashCode());
            } else {
                random = new Random(column.hashCode());
            }

            generatorMap.put(column, databaseSupport.getDataGenerator(column, random));
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int rowCount = 0;
            for (int i = 0; i < rows; i++) {

                int colCount = 1;
                for (Column column : filteredColumns) {

                    DataGenerator<?> dataGenerator = generatorMap.get(column);

                    dataGenerator.generateAndSet(connection, ps, colCount);

                    colCount++;
                }

                ps.addBatch();

                if (++rowCount % batchSize == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }


    }


    public static class Builder {

        private final Connection connection;
        private final Database database;
        private final DatabaseSupport databaseSupport;

        private Table table;
        private int rows = 1000;
        private int batchSize = 128;

        public Builder(Connection connection, Database database, DatabaseSupport databaseSupport) {
            this.connection = connection;
            this.database = database;
            this.databaseSupport = databaseSupport;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder table(String tableName) {
            this.table = database.getTable(tableName);
            return this;
        }

        public Builder table(Table table) {
            this.table = table;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public TableFiller build() {
            return new TableFiller(this);
        }
    }

    private TableFiller(Builder builder) {
        this.connection = builder.connection;
        this.table = builder.table;
        this.database = builder.database;
        this.rows = builder.rows;
        this.batchSize = builder.batchSize;
        this.databaseSupport = builder.databaseSupport;
    }
}
