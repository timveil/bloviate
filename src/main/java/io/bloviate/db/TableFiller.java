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
import org.apache.commons.lang3.time.StopWatch;
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
    private final DatabaseConfiguration databaseConfiguration;
    private final Table table;

    @Override
    public void fill() throws SQLException {

        String sql = table.insertString();

        logger.trace(sql);

        Map<Column, DataGenerator<?>> generatorMap = new HashMap<>();
        Map<Column, Long> seedMap = new HashMap<>();
        Map<Column, Long> maxInvocationMap = new HashMap<>();

        List<Column> filteredColumns = table.filteredColumns();

        DatabaseSupport databaseSupport = databaseConfiguration.databaseSupport();

        for (Column column : filteredColumns) {

            long seed;

            Column associatedPrimaryKeyColumn = DatabaseUtils.getAssociatedPrimaryKeyColumn(database, table, column);

            if (associatedPrimaryKeyColumn != null) {

                // check to see if table has custom configuration
                TableConfiguration primaryTableConfiguration = databaseConfiguration.tableConfiguration(associatedPrimaryKeyColumn.tableName());

                if (primaryTableConfiguration != null) {
                    // this is the number of rows in the primary table.  a foreign key random generator can't be called more than this number of times.
                    maxInvocationMap.put(column, primaryTableConfiguration.rowCount());
                }

                seed = associatedPrimaryKeyColumn.hashCode();
            } else {
                seed = column.hashCode();
            }

            generatorMap.put(column, databaseSupport.getDataGenerator(column, new Random(seed)));
            seedMap.put(column, seed);
        }

        int batchSize = databaseConfiguration.batchSize();

        TableConfiguration tableConfiguration = databaseConfiguration.tableConfiguration(table.name());

        long rowCount = databaseConfiguration.defaultRowCount();
        if (tableConfiguration != null) {
            rowCount = tableConfiguration.rowCount();
        }

        logger.info("filling table [{}] with [{}] rows", table.name(), rowCount);

        StopWatch tableWatch = new StopWatch(String.format("filled table [%s] in", table.name()));
        tableWatch.start();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int rowCounter = 0;
            for (long i = 0; i < rowCount; i++) {

                int colCounter = 1;
                for (Column column : filteredColumns) {

                    DataGenerator<?> dataGenerator = generatorMap.get(column);

                    if (maxInvocationMap.containsKey(column)) {
                        long maxInvocations = maxInvocationMap.get(column);

                        if (rowCounter != 0 && rowCounter % maxInvocations == 0) {
                            dataGenerator.setSeed(seedMap.get(column));
                        }
                    }

                    dataGenerator.generateAndSet(connection, ps, colCounter);

                    colCounter++;
                }

                ps.addBatch();

                if (++rowCounter % batchSize == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }

        tableWatch.stop();

        logger.info(tableWatch.toString());

    }


    public static class Builder {

        private final Connection connection;
        private final Database database;
        private final DatabaseConfiguration databaseConfiguration;

        private Table table;

        public Builder(Connection connection, Database database, DatabaseConfiguration databaseConfiguration) {
            this.connection = connection;
            this.database = database;
            this.databaseConfiguration = databaseConfiguration;
        }

        public Builder table(String tableName) {
            this.table = database.getTable(tableName);
            return this;
        }

        public Builder table(Table table) {
            this.table = table;
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
        this.databaseConfiguration = builder.databaseConfiguration;
    }
}
