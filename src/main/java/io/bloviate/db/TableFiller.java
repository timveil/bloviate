/*
 * Copyright (c) 2021 Tim Veil
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

import io.bloviate.db.metadata.*;
import io.bloviate.ext.DatabaseSupport;
import io.bloviate.gen.ColumnValue;
import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.PrimaryKeyGenerator;
import io.bloviate.gen.SimplePrimaryKeyGenerator;
import io.bloviate.util.DatabaseUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class TableFiller implements Fillable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final Database database;
    private final DatabaseConfiguration databaseConfiguration;
    private final Table table;

    public void fillNew() throws SQLException {
        List<Column> columns = table.filteredColumns(false, false, true);


        DatabaseSupport databaseSupport = databaseConfiguration.databaseSupport();

        TableConfiguration tableConfiguration = databaseConfiguration.tableConfiguration(table.name());
        PrimaryKeyConfiguration primaryKeyConfiguration = tableConfiguration != null ? tableConfiguration.getPrimaryKeyConfiguration() : null;

        int batchSize = databaseConfiguration.batchSize();
        long rowCount = databaseConfiguration.defaultRowCount();
        boolean tableHasPrimaryKey = table.hasPrimaryKey();

        if (tableConfiguration != null) {
            rowCount = tableConfiguration.getRowCount();
        }

        for (Column column : columns) {
            ColumnGenerator generator = getColumnGenerator(databaseSupport, table, column, tableConfiguration);

        }

    }

    ColumnGenerator getColumnGenerator(DatabaseSupport databaseSupport, Table table, Column column, TableConfiguration tableConfiguration) {

        ColumnGenerator columnGenerator = null;

        if (table.isForeignKey(column)) {
            columnGenerator = getForeignKeyGenerator(databaseSupport, tableConfiguration, column);
        } else {
            columnGenerator = getNonForeignKeyGenerator(databaseSupport, tableConfiguration, column);
        }

        return columnGenerator;

    }

    @Override
    public void fill() throws SQLException {

        List<Column> columns = table.filteredColumns(false, false, true);
        String sql = table.insertString(columns);

        logger.trace(sql);

        TreeMap<Column, DataGenerator<?>> generatorMap = new TreeMap<>();
        Map<Column, Random> randomMap = new HashMap<>();
        Map<Column, Long> seedMap = new HashMap<>();
        Map<Column, Long> maxInvocationMap = new HashMap<>();

        DatabaseSupport databaseSupport = databaseConfiguration.databaseSupport();

        TableConfiguration tableConfiguration = databaseConfiguration.tableConfiguration(table.name());
        PrimaryKeyConfiguration primaryKeyConfiguration = tableConfiguration != null ? tableConfiguration.getPrimaryKeyConfiguration() : null;

        int batchSize = databaseConfiguration.batchSize();
        long rowCount = databaseConfiguration.defaultRowCount();
        boolean tableHasPrimaryKey = table.hasPrimaryKey();

        if (tableConfiguration != null) {
            rowCount = tableConfiguration.getRowCount();
        }

        logger.info("filling table [{}] with [{}] rows; batch size is [{}]", table.name(), rowCount, batchSize);


        PrimaryKeyGenerator primaryKeyGenerator = null;

        // handle tables primary key which may include multiple columns.  a PK column can also be a FK and that should be handled here
        if (tableHasPrimaryKey) {

            if (primaryKeyConfiguration != null) {

                primaryKeyGenerator = primaryKeyConfiguration.primaryKeyGenerator();

            } else {

                PrimaryKey primaryKey = table.primaryKey();

                List<KeyColumn> keyColumns = primaryKey.keyColumns();

                Map<KeyColumn, DataGenerator<?>> dataGenerators = new HashMap<>();

                for (KeyColumn kc : keyColumns) {

                    Column column = kc.column();

                    DataGenerator<?> dataGenerator = null;

                    if (table.isForeignKey(column)) {
                        dataGenerator = getForeignKeyGenerator(databaseSupport, tableConfiguration, column);
                    } else {
                        dataGenerator = databaseSupport.getDataGenerator(column);
                    }

                    dataGenerators.put(kc, dataGenerator);

                }

                primaryKeyGenerator = new SimplePrimaryKeyGenerator.Builder(dataGenerators).build();

            }
        }

        // handle foreign keys that are not part of the primary key above
        if (table.hasForeignKeys()) {
            List<ForeignKey> foreignKeys = table.foreignKeys();

            for (ForeignKey fk : foreignKeys) {
                for (KeyColumn keyColumn : fk.foreignKeyColumns()) {
                    Column column = keyColumn.column();

                    if (!table.isPrimaryKey(column)) {
                        generatorMap.put(column, getForeignKeyGenerator(databaseSupport, tableConfiguration, column));
                    }
                }
            }
        }

        // handle remaining columns that need a value
        for (Column column : table.filteredColumns(true, true, true)) {
            if (tableConfiguration != null && tableConfiguration.columnConfiguration(column.name()) != null) {
                generatorMap.put(column, tableConfiguration.columnConfiguration(column.name()).dataGenerator());
            } else {
                generatorMap.put(column, databaseSupport.getDataGenerator(column));
            }
        }

        StopWatch tableWatch = new StopWatch(String.format("filled table [%s] in", table.name()));
        tableWatch.start();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int rowCounter = 0;
            for (long i = 0; i < rowCount; i++) {

                Map<Column, ColumnValue<?>> primaryValues = null;

                if (tableHasPrimaryKey) {
                    PrimaryKey primaryKey = table.primaryKey();

                    primaryValues = primaryKeyGenerator.generate(primaryKey, new Random());  // todo; can't create new must get existing
                }

                int colCounter = 1;
                for (Column column : table.filteredColumns(false, false, true)) {

                    if (table.isPrimaryKey(column)) {

                        ColumnValue<?> columnValue = Objects.requireNonNull(primaryValues).get(column);

                        // we don't need to be terribly concerned about the correct generator here because value has already been generated.  all we really care about is type.
                        DataGenerator<Object> dataGenerator = databaseSupport.getDataGenerator(column);

                        dataGenerator.set(connection, ps, colCounter, columnValue.value());

                    } else {
                        Random random = randomMap.get(column);

                        if (maxInvocationMap.containsKey(column)) {
                            long maxInvocations = maxInvocationMap.get(column);

                            if (rowCounter != 0 && rowCounter % maxInvocations == 0) {
                                random.setSeed(seedMap.get(column));
                            }
                        }


                        DataGenerator<?> dataGenerator = generatorMap.get(column);

                        dataGenerator.generateAndSet(connection, ps, colCounter, random);
                    }

                    colCounter++;
                }

                ps.addBatch();

                if (++rowCounter % batchSize == 0) {
                    logger.info("executing batch for row count {} on table {}", rowCounter, table.name());
                    ps.executeBatch();
                }
            }

            ps.executeBatch();

        } finally {
            tableWatch.stop();
        }

        logger.info(tableWatch.toString());

    }

    private ColumnGenerator getForeignKeyGenerator(DatabaseSupport databaseSupport, TableConfiguration tableConfiguration, Column foreignKeyColumn) {

        DataGenerator<?> dataGenerator = null;

        if (tableConfiguration != null && tableConfiguration.columnConfiguration(foreignKeyColumn.name()) != null) {
            return tableConfiguration.columnConfiguration(foreignKeyColumn.name()).dataGenerator();
        } else {

            Column associatedPrimaryKeyColumn = DatabaseUtils.getAssociatedPrimaryKeyColumn(database, table, foreignKeyColumn);

            Objects.requireNonNull(associatedPrimaryKeyColumn, String.format("no associated primary key column found for column %s", foreignKeyColumn));

            TableConfiguration primaryTableConfiguration = databaseConfiguration.tableConfiguration(associatedPrimaryKeyColumn.tableName());

            if (primaryTableConfiguration != null) {

                ColumnConfiguration primaryKeyColumnConfiguration = primaryTableConfiguration.columnConfiguration(associatedPrimaryKeyColumn.name());

                if (primaryKeyColumnConfiguration != null) {
                    dataGenerator = primaryKeyColumnConfiguration.dataGenerator();
                }

            }

            if (dataGenerator == null) {
                dataGenerator = databaseSupport.getDataGenerator(associatedPrimaryKeyColumn);
            }

            return dataGenerator;
        }
    }

    private DataGenerator<?> getNonForeignKeyGenerator(DatabaseSupport databaseSupport, TableConfiguration tableConfiguration, Column column) {

        if (tableConfiguration != null && tableConfiguration.columnConfiguration(column.name()) != null) {
            return tableConfiguration.columnConfiguration(column.name()).dataGenerator();
        }

        return databaseSupport.getDataGenerator(column);
    }


    public static class Builder {

        private final Connection connection;
        private final Database database;
        private final DatabaseConfiguration databaseConfiguration;

        private Table table;

        public Builder(Connection connection, DatabaseConfiguration databaseConfiguration, Database database) {
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
