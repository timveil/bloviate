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

import io.bloviate.ext.DatabaseSupport;
import io.bloviate.ext.GeneratorRegistry;
import io.bloviate.gen.DataGenerator;
import io.bloviate.util.DatabaseUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;

/**
 * Fills a database table with generated data.
 * This class handles the population of a single table with synthetic data,
 * respecting foreign key constraints and using appropriate data generators
 * for each column type.
 *
 * @since 1.0.0
 */
public class TableFiller implements Fillable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final Database database;
    private final DatabaseConfiguration databaseConfiguration;
    private final Table table;

    /**
     * Constructs a new TableFiller.
     *
     * @param connection the database connection to use for filling the table
     * @param database the database metadata containing table relationships
     * @param databaseConfiguration the configuration settings for the fill operation
     * @param table the table to be filled with data
     */
    public TableFiller(Connection connection, Database database, DatabaseConfiguration databaseConfiguration, Table table) {
        this.connection = connection;
        this.database = database;
        this.databaseConfiguration = databaseConfiguration;
        this.table = table;
    }

    @Override
    public void fill() throws SQLException {

        String sql = table.insertString();

        logger.trace(sql);

        // The fill loop is the hot path: it runs once per cell (rowCount * columnCount times).
        // To keep it allocation- and lookup-free, everything is resolved up front into arrays
        // indexed by column position, so the inner loop only does positional array reads instead
        // of hashing the Column record on every cell (the issue #447 micro-optimization).
        List<Column> filteredColumns = table.filteredColumns();
        int columnCount = filteredColumns.size();

        DataGenerator<?>[] generators = new DataGenerator<?>[columnCount];
        long[] reseedSeeds = new long[columnCount];
        // 0 means "this column never reseeds"; a positive value is the parent row count past which
        // a foreign-key generator must be reseeded to stay within the parent key space (wraparound)
        long[] maxInvocations = new long[columnCount];

        DatabaseSupport databaseSupport = databaseConfiguration.databaseSupport();

        TableConfiguration tableConfiguration = databaseConfiguration.tableConfiguration(table.name());

        long baseSeed = databaseConfiguration.seed();

        for (int idx = 0; idx < columnCount; idx++) {

            Column column = filteredColumns.get(idx);

            long seed;

            Column associatedPrimaryKeyColumn = DatabaseUtils.getAssociatedPrimaryKeyColumn(database, table, column);

            if (associatedPrimaryKeyColumn != null) {

                // check to see if table has custom configuration
                TableConfiguration primaryTableConfiguration = databaseConfiguration.tableConfiguration(associatedPrimaryKeyColumn.tableName());

                if (primaryTableConfiguration != null) {
                    // this is the number of rows in the primary table.  a foreign key random generator can't be called more than this number of times.
                    maxInvocations[idx] = primaryTableConfiguration.rowCount();
                }

                // seed the foreign key from its associated primary key so the two line up;
                // columnSeed is a pure function of the column, so both resolve to the same seed
                seed = DatabaseUtils.columnSeed(associatedPrimaryKeyColumn, baseSeed);
            } else {
                seed = DatabaseUtils.columnSeed(column, baseSeed);
            }

            // resolve the generator by precedence; the generator is always seeded by the engine
            // so it stays reproducible regardless of which path provides it:
            //   per-column config > custom registry (name > typeName > JDBCType) > support default
            Random random = new Random(seed);

            ColumnConfiguration columnConfiguration = tableConfiguration != null
                    ? tableConfiguration.columnConfiguration(column.name())
                    : null;

            DataGenerator<?> dataGenerator;
            if (columnConfiguration != null) {
                dataGenerator = columnConfiguration.generatorFactory().create(random);
            } else {
                GeneratorRegistry registry = databaseConfiguration.generatorRegistry();
                DataGenerator<?> custom = registry != null ? registry.resolve(column, random) : null;
                dataGenerator = custom != null ? custom : databaseSupport.getDataGenerator(column, random);
            }

            generators[idx] = dataGenerator;
            reseedSeeds[idx] = seed;
        }

        int batchSize = databaseConfiguration.batchSize();

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

                for (int col = 0; col < columnCount; col++) {

                    DataGenerator<?> dataGenerator = generators[col];

                    long maxInvocation = maxInvocations[col];
                    if (maxInvocation > 0 && rowCounter != 0 && rowCounter % maxInvocation == 0) {
                        // foreign-key generator has exhausted the parent key space; reseed so it
                        // replays the same parent keys and never references a non-existent parent
                        dataGenerator.setSeed(reseedSeeds[col]);
                    }

                    dataGenerator.generateAndSet(connection, ps, col + 1);
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
