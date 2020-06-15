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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.StringJoiner;

public class TableFiller implements Fillable {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final Table table;
    private final int rows;
    private final int batchSize;

    @Override
    public void fill() throws SQLException {

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

                    if (table.isForeignKey(column)) {
                        logger.debug("column is foreign key: {}", column);
                    }

                    column.getDataGenerator().generateAndSet(connection, ps, colCount);

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
        private final Table table;

        private int rows = 1000;
        private int batchSize = 128;

        public Builder(Connection connection, Table table) {
            this.connection = connection;
            this.table = table;
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
        this.table = builder.table;
        this.rows = builder.rows;
        this.batchSize = builder.batchSize;
    }
}
