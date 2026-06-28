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
import io.bloviate.gen.IndexedDataGenerator;
import io.bloviate.util.DatabaseUtils;
import io.bloviate.util.Mixers;
import io.bloviate.util.RandomGenerators;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

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

    /** How this filler commits; resolved from the builder override or {@link DatabaseConfiguration}. */
    private final CommitStrategy commitStrategy;

    /** True when this filler handles a sub-range (one partition) of the table rather than all rows. */
    private final boolean partitioned;
    private final long rangeStartInclusive;
    private final long rangeEndExclusive;

    /**
     * Constructs a new TableFiller using the configuration's {@link CommitStrategy}.
     *
     * @param connection the database connection to use for filling the table
     * @param database the database metadata containing table relationships
     * @param databaseConfiguration the configuration settings for the fill operation
     * @param table the table to be filled with data
     */
    public TableFiller(Connection connection, Database database, DatabaseConfiguration databaseConfiguration, Table table) {
        this(connection, database, databaseConfiguration, table, databaseConfiguration.commitStrategy());
    }

    /**
     * Constructs a new TableFiller with an explicit {@link CommitStrategy} override.
     *
     * @param connection the database connection to use for filling the table
     * @param database the database metadata containing table relationships
     * @param databaseConfiguration the configuration settings for the fill operation
     * @param table the table to be filled with data
     * @param commitStrategy the commit strategy to use; falls back to the configuration's when null
     */
    public TableFiller(Connection connection, Database database, DatabaseConfiguration databaseConfiguration, Table table, CommitStrategy commitStrategy) {
        this.connection = connection;
        this.database = database;
        this.databaseConfiguration = databaseConfiguration;
        this.table = table;
        this.commitStrategy = commitStrategy != null ? commitStrategy : databaseConfiguration.commitStrategy();
        this.partitioned = false;
        this.rangeStartInclusive = 0;
        this.rangeEndExclusive = 0;
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
        GeneratorRegistry registry = databaseConfiguration.generatorRegistry();

        TableConfiguration tableConfiguration = databaseConfiguration.tableConfiguration(table.name());

        long baseSeed = databaseConfiguration.seed();

        // value constraints (CHECK / enum) for this table's columns, so generated values conform
        // (issue #479). Empty for databases without support; keyed by lower-cased column name.
        String schema = filteredColumns.isEmpty() ? null : filteredColumns.get(0).schema();
        Map<String, ColumnConstraint> constraints = databaseSupport.readConstraints(connection, schema, table.name());

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
            RandomGenerator random = RandomGenerators.create(seed);

            ColumnConfiguration columnConfiguration = tableConfiguration != null
                    ? tableConfiguration.columnConfiguration(column.name())
                    : null;

            DataGenerator<?> dataGenerator;
            if (columnConfiguration != null) {
                dataGenerator = columnConfiguration.generatorFactory().create(random);
            } else {
                DataGenerator<?> custom = registry != null ? registry.resolve(column, random) : null;
                if (custom != null) {
                    dataGenerator = custom;
                } else {
                    // honor a CHECK/enum constraint when one applies and the user hasn't overridden the column
                    ColumnConstraint constraint = constraints.get(column.name().toLowerCase(Locale.ROOT));
                    DataGenerator<?> constrained = constraint != null ? ConstraintGenerators.create(column, constraint, random) : null;
                    if (constraint != null && constrained == null) {
                        logger.warn("constraint on column [{}.{}] could not be applied to its {} type; using the type default",
                                table.name(), column.name(), column.jdbcType());
                    }
                    dataGenerator = constrained != null ? constrained : databaseSupport.getDataGenerator(column, random);
                }
            }

            generators[idx] = dataGenerator;
            reseedSeeds[idx] = seed;
        }

        int batchSize = databaseConfiguration.batchSize();

        long totalRowCount = databaseConfiguration.defaultRowCount();
        if (tableConfiguration != null) {
            totalRowCount = tableConfiguration.rowCount();
        }

        // when a sub-range is configured this filler handles one partition of the table; otherwise
        // it fills the whole table on the original, byte-for-byte-unchanged path
        long startRow = partitioned ? rangeStartInclusive : 0;
        long endRow = partitioned ? Math.min(rangeEndExclusive, totalRowCount) : totalRowCount;

        if (partitioned) {
            logger.info("filling table [{}] rows [{}, {}) of [{}]", table.name(), startRow, endRow, totalRowCount);
        } else {
            logger.info("filling table [{}] with [{}] rows", table.name(), totalRowCount);
        }

        StopWatch tableWatch = new StopWatch(String.format("filled table [%s] in", table.name()));
        tableWatch.start();

        // For anything other than CONNECTION_DEFAULT the engine owns the transaction: autocommit off
        // for the fill, commit at the configured cadence, roll back on error, and restore the prior
        // autocommit in the finally. CONNECTION_DEFAULT leaves the connection exactly as the caller
        // (or pool) configured it — the original, back-compatible behavior.
        boolean manageTransaction = commitStrategy.managesTransaction();
        boolean previousAutoCommit = manageTransaction && connection.getAutoCommit();
        if (manageTransaction) {
            connection.setAutoCommit(false);
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (partitioned) {
                // position every generator at the partition's first absolute row so its values match
                // the sequential fill: keys/foreign keys stay byte-identical, while non-key random
                // columns are reseeded per partition (deterministic for the chosen partition count)
                seekGeneratorsTo(generators, reseedSeeds, maxInvocations, startRow);
            }

            int batchesSinceCommit = 0;
            long produced = 0;
            for (long i = startRow; i < endRow; i++) {

                for (int col = 0; col < columnCount; col++) {

                    DataGenerator<?> dataGenerator = generators[col];

                    long maxInvocation = maxInvocations[col];
                    if (maxInvocation > 0 && i != 0 && i % maxInvocation == 0) {
                        // foreign-key generator has exhausted the parent key space; reseed its random
                        // source so it replays the same parent keys and never references a non-existent
                        // parent (positional generators ignore the unused RNG, so this is a no-op for
                        // them; the index drives their wraparound by formula)
                        dataGenerator.reseed(reseedSeeds[col]);
                    }

                    dataGenerator.generateAndSet(connection, ps, col + 1);
                }

                ps.addBatch();

                if (++produced % batchSize == 0) {
                    ps.executeBatch();
                    if (commitStrategy.mode() == CommitStrategy.Mode.EVERY_N_BATCHES
                            && ++batchesSinceCommit % commitStrategy.batches() == 0) {
                        connection.commit();
                    }
                }
            }

            ps.executeBatch();

            if (manageTransaction) {
                // commit the final partial batch (and any whole batches not yet committed under EVERY_N_BATCHES)
                connection.commit();
            }
        } catch (SQLException e) {
            if (manageTransaction) {
                connection.rollback();
            }
            throw e;
        } finally {
            if (manageTransaction) {
                connection.setAutoCommit(previousAutoCommit);
            }
        }

        tableWatch.stop();

        logger.info(tableWatch.toString());

    }

    /**
     * Positions every column generator so the next value produced is the one for absolute row
     * {@code startRow}, using a three-way policy that keeps a partitioned fill consistent with a
     * sequential one:
     * <ul>
     *   <li><b>Positional generators</b> ({@link IndexedDataGenerator} — keys, sequences,
     *       permutations, prefixes) seek directly to the absolute index, so they are byte-identical
     *       to the sequential fill regardless of partition count.</li>
     *   <li><b>Foreign-key random-replay columns</b> ({@code maxInvocations[col] > 0}) reseed and
     *       advance {@code startRow % maxInvocation} draws into the parent key cycle, so the partition
     *       continues the exact parent-key sequence and never references a missing parent.</li>
     *   <li><b>Plain non-key random columns</b> are reseeded from a partition-derived seed; their
     *       values are deterministic for the chosen partition count but (by design) need not match a
     *       different partitioning, as they carry no cross-row contract.</li>
     * </ul>
     */
    private void seekGeneratorsTo(DataGenerator<?>[] generators, long[] reseedSeeds, long[] maxInvocations, long startRow) {
        for (int col = 0; col < generators.length; col++) {
            DataGenerator<?> generator = generators[col];
            if (generator instanceof IndexedDataGenerator indexed) {
                indexed.seek(startRow);
            } else if (maxInvocations[col] > 0) {
                generator.reseed(reseedSeeds[col]);
                long advance = startRow % maxInvocations[col];
                for (long k = 0; k < advance; k++) {
                    generator.generate();
                }
            } else {
                generator.reseed(Mixers.splitmix64(reseedSeeds[col] + startRow));
            }
        }
    }


    public static class Builder {

        private final Connection connection;
        private final Database database;
        private final DatabaseConfiguration databaseConfiguration;

        private Table table;
        private CommitStrategy commitStrategy;
        private boolean partitioned;
        private long rangeStartInclusive;
        private long rangeEndExclusive;

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

        /**
         * Overrides the {@link CommitStrategy} for this table fill. When unset (or null), the
         * configuration's strategy is used.
         *
         * @param commitStrategy the commit strategy to use, or null for the configuration default
         * @return this builder
         */
        public Builder commitStrategy(CommitStrategy commitStrategy) {
            this.commitStrategy = commitStrategy;
            return this;
        }

        /**
         * Restricts this filler to a contiguous sub-range of the table's rows — one partition of an
         * intra-table parallel fill. Generators are positioned to {@code startInclusive} so the
         * produced values match the sequential fill for the corresponding rows (see
         * {@link TableFiller#seekGeneratorsTo}). When unset, the whole table is filled.
         *
         * @param startInclusive the first absolute (0-based) row to fill, inclusive
         * @param endExclusive the row to stop at, exclusive
         * @return this builder
         * @throws IllegalArgumentException if the range is negative or empty/inverted
         */
        public Builder rowRange(long startInclusive, long endExclusive) {
            if (startInclusive < 0 || endExclusive < startInclusive) {
                throw new IllegalArgumentException("invalid row range [" + startInclusive + ", " + endExclusive + ")");
            }
            this.rangeStartInclusive = startInclusive;
            this.rangeEndExclusive = endExclusive;
            this.partitioned = true;
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
        this.commitStrategy = builder.commitStrategy != null ? builder.commitStrategy : builder.databaseConfiguration.commitStrategy();
        this.partitioned = builder.partitioned;
        this.rangeStartInclusive = builder.rangeStartInclusive;
        this.rangeEndExclusive = builder.rangeEndExclusive;
    }
}
