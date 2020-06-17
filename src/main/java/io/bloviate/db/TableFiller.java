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

import java.sql.*;
import java.util.*;

public class TableFiller implements Fillable {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final Database database;
    private final String tableName;
    private final int rows;
    private final int batchSize;

    @Override
    public void fill() throws SQLException {

        Table table = database.getTable(tableName);

        StringJoiner nameJoiner = new StringJoiner(",");
        StringJoiner valueJoiner = new StringJoiner(",");

        for (Column column : table.getColumns()) {
            nameJoiner.add(column.getName());
            valueJoiner.add("?");
        }

        String valueString = valueJoiner.toString();
        String nameString = nameJoiner.toString();

        String sql = String.format("insert into %s (%s) values (%s)", table.getName(), nameString, valueString);

        logger.debug(sql);

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int rowCount = 0;
            for (int i = 0; i < rows; i++) {

                int colCount = 1;
                for (Column column : table.getColumns()) {

                    ForeignKey fk = table.getForeignKey(column);
                    PrimaryKey pk = table.getPrimaryKey(column);

                    // problem is that some kys are both pk & fk.  either i need to populate key cache again or recursively link back to source

                    if (fk != null) {

                        PrimaryKey referencedPk = database.getRootPrimaryKey(fk.getForeignTable(), fk.getForeignKey());
                        //Table referencedTable = database.getTable(fk.getForeignTable());
                        //PrimaryKey referencedPk = referencedTable.getPrimaryKey(fk.getForeignKey());

                        column.getDataGenerator().set(connection, ps, colCount, referencedPk.getRandomKey());

                    } else if (pk != null) {
                        Object pkValue = column.getDataGenerator().generateAndSet(connection, ps, colCount);

                        pk.addKey(pkValue);

                    } else {

                        column.getDataGenerator().generateAndSet(connection, ps, colCount);

                    }

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
        private final String tableName;

        private int rows = 1000;
        private int batchSize = 128;

        public Builder(Connection connection, Database database, String tableName) {
            this.connection = connection;
            this.database = database;
            this.tableName = tableName;
        }


        public Builder rows(int rows) {
            this.rows = rows;
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
        this.tableName = builder.tableName;
        this.database = builder.database;
        this.rows = builder.rows;
        this.batchSize = builder.batchSize;
    }
}
