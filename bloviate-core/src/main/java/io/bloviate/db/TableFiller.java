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
import io.bloviate.util.IndexedRandom;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fills a database table with generated data.
 * This class handles the population of a single table with synthetic data,
 * respecting foreign key constraints and using appropriate data generators
 * for each column type.
 *
 * @since 1.0.0
 */
public class TableFiller implements Fillable {

    private static final Logger logger = LoggerFactory.getLogger(TableFiller.class);

    private final Connection connection;
    private final Database database;
    private final DatabaseConfiguration databaseConfiguration;
    private final Table table;

    /** How this filler commits; resolved from the builder override or {@link DatabaseConfiguration}. */
    private final CommitStrategy commitStrategy;

    /** Pre-resolved constraint metadata, or null to read from the catalog at fill time. */
    private final Map<String, ColumnConstraint> constraints;

    /** True when this filler handles a sub-range (one partition) of the table rather than all rows. */
    private final boolean partitioned;
    private final long rangeStartInclusive;
    private final long rangeEndExclusive;

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
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.databaseConfiguration = Objects.requireNonNull(databaseConfiguration, "databaseConfiguration must not be null");
        this.table = Objects.requireNonNull(table, "table must not be null");
        this.commitStrategy = commitStrategy != null ? commitStrategy : databaseConfiguration.commitStrategy();
        this.constraints = null;
        this.partitioned = false;
        this.rangeStartInclusive = 0;
        this.rangeEndExclusive = 0;
    }

    /**
     * Fills this single table with generated data, respecting its column types and foreign-key
     * relationships.
     *
     * <p>Generators are resolved once per column (per-column override, then custom registry, then
     * any applicable {@code CHECK}/enum constraint, then the {@link DatabaseSupport} default) and the
     * engine seeds each one for reproducibility. Rows are then produced in batches of the configured
     * size and inserted through a {@link PreparedStatement}. Unless the {@link CommitStrategy} leaves
     * the connection on its default, the engine owns the transaction: autocommit is turned off for the
     * fill, commits happen at the configured cadence, the work is rolled back on error, and the prior
     * autocommit setting is restored on completion. When a row range is configured this fills only that
     * one partition; otherwise it fills the whole table.
     *
     * @throws SQLException if any database access error occurs during the fill operation
     */
    @Override
    public void fill() throws SQLException {

        // The fill loop is the hot path: it runs once per cell (rowCount * columnCount times).
        // To keep it allocation- and lookup-free, everything is resolved up front into arrays
        // indexed by column position, so the inner loop only does positional array reads instead
        // of hashing the Column record on every cell (the issue #447 micro-optimization).
        List<Column> filteredColumns = table.filteredColumns();
        int columnCount = filteredColumns.size();

        DataGenerator<?>[] generators = new DataGenerator<?>[columnCount];
        long[] reseedSeeds = new long[columnCount];
        // 0 means "this column never reseeds"; a positive value is the parent row count past which
        // a foreign-key generator must wrap around to stay within the parent key space
        long[] maxInvocations = new long[columnCount];
        // the column's IndexedRandom when its generator is positionable (the engine repositions it
        // to the absolute row index before every row); null keeps the legacy sequential-draw path
        IndexedRandom[] positionables = new IndexedRandom[columnCount];

        DatabaseSupport databaseSupport = databaseConfiguration.databaseSupport();
        GeneratorRegistry registry = databaseConfiguration.generatorRegistry();

        TableConfiguration tableConfiguration = databaseConfiguration.tableConfiguration(table.name());

        long baseSeed = databaseConfiguration.seed();

        // value constraints (CHECK / enum) for this table's columns, so generated values conform
        // (issue #479). Empty for databases without support; keyed by lower-cased column name.
        // A pre-resolved map (from DatabaseFiller's per-fill cache) skips the catalog queries,
        // which an intra-table parallel fill would otherwise repeat once per partition.
        Map<String, ColumnConstraint> constraints = this.constraints;
        if (constraints == null) {
            String schema = filteredColumns.isEmpty() ? null : filteredColumns.getFirst().schema();
            constraints = databaseSupport.readConstraints(connection, schema, table.name());
        }

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
            //   per-column config > custom registry (name > typeName > JDBCType) > support default.
            // The source is an IndexedRandom so positionable generators can be repositioned to the
            // absolute row index before every row (see the fill loop); it mutates in place, so
            // delegates that captured the reference at construction follow automatically.
            IndexedRandom random = new IndexedRandom(seed);

            ColumnConfiguration columnConfiguration = tableConfiguration != null
                    ? tableConfiguration.columnConfiguration(column.name())
                    : null;

            DataGenerator<?> dataGenerator;
            String source;
            if (columnConfiguration != null) {
                dataGenerator = columnConfiguration.generatorFactory().create(random);
                source = "column-config";
            } else {
                DataGenerator<?> custom = registry != null ? registry.resolve(column, random) : null;
                if (custom != null) {
                    dataGenerator = custom;
                    source = "registry";
                } else {
                    // honor a CHECK/enum constraint when one applies and the user hasn't overridden the column
                    ColumnConstraint constraint = constraints.get(column.name().toLowerCase(Locale.ROOT));
                    DataGenerator<?> constrained = constraint != null ? ConstraintGenerators.create(column, constraint, random) : null;
                    if (constraint != null && constrained == null) {
                        logger.warn("constraint on column [{}.{}] could not be applied to its {} type; using the type default",
                                table.name(), column.name(), column.jdbcType());
                    }
                    if (constrained != null) {
                        dataGenerator = constrained;
                        source = "constraint";
                    } else {
                        dataGenerator = databaseSupport.getDataGenerator(column, random);
                        source = "support-default";
                    }
                }
            }

            // per-column resolution is the most useful diagnostic for a data-gen library: it reveals
            // which generator (incl. semantic/datafaker matches) each column got, and by which path
            logger.debug("column [{}.{}] ({}) resolved to generator [{}] via {}",
                    table.name(), column.name(), column.jdbcType(), dataGenerator.getClass().getSimpleName(), source);

            generators[idx] = dataGenerator;
            reseedSeeds[idx] = seed;
            if (dataGenerator.positionable()) {
                positionables[idx] = random;
            }
        }

        // Built after generator resolution, not before: a generator decides how its value is bound,
        // and a type the driver cannot bind directly must be constructed in SQL instead (BigQuery's
        // PARSE_JSON(?) / ST_GEOGFROMTEXT(?)). Nearly always this is the same all-placeholder
        // statement as before, since DataGenerator.valueExpression() defaults to "?".
        List<String> valueExpressions = new ArrayList<>(columnCount);
        for (DataGenerator<?> generator : generators) {
            valueExpressions.add(generator.valueExpression());
        }

        String sql = table.insertString(connection.getMetaData().getIdentifierQuoteString(), valueExpressions);

        logger.trace("{}", sql);

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
            logger.debug("filling table [{}] rows [{}, {}) of [{}]", table.name(), startRow, endRow, totalRowCount);
        } else {
            logger.debug("filling table [{}] with [{}] rows", table.name(), totalRowCount);
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

        // captures a fill/rollback failure so autocommit restore (below) can attach to it rather than
        // replace it; null when the fill succeeds
        SQLException failure = null;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            if (partitioned) {
                // position every generator at the partition's first absolute row so its values match
                // the sequential fill: positionable columns are already repositioned before every row
                // (byte-identical to the sequential fill for any partition count), so only indexed
                // counters and legacy non-positionable generators need explicit seeking here
                seekGeneratorsTo(generators, positionables, reseedSeeds, maxInvocations, startRow);
            }

            int batchesSinceCommit = 0;
            long produced = 0;
            for (long i = startRow; i < endRow; i++) {

                for (int col = 0; col < columnCount; col++) {

                    DataGenerator<?> dataGenerator = generators[col];

                    long maxInvocation = maxInvocations[col];
                    IndexedRandom positioned = positionables[col];
                    if (positioned != null) {
                        // position the random source at this row's absolute index so the value is a
                        // pure function of (columnSeed, rowIndex); a foreign-key column positions at
                        // the parent's index instead (i mod parent rows), which both replays the
                        // parent's exact value and makes wraparound a formula rather than a reseed
                        positioned.position(maxInvocation > 0 ? i % maxInvocation : i);
                    } else if (maxInvocation > 0 && i != 0 && i % maxInvocation == 0) {
                        // legacy path (non-positionable generator): the foreign-key generator has
                        // exhausted the parent key space; reseed its random source so it replays the
                        // same parent keys and never references a non-existent parent
                        dataGenerator.reseed(reseedSeeds[col]);
                    }

                    dataGenerator.generateAndSet(connection, ps, col + 1);
                }

                ps.addBatch();

                if (++produced % batchSize == 0) {
                    ps.executeBatch();
                    // explicit clearBatch after executeBatch: the JDBC spec already clears the batch,
                    // but this makes the O(batchSize) in-flight bound explicit and is cheap insurance
                    // against drivers / rewriteBatchedInserts paths that might otherwise retain the
                    // submitted row references
                    ps.clearBatch();
                    if (commitStrategy.mode() == CommitStrategy.Mode.EVERY_N_BATCHES
                            && ++batchesSinceCommit % commitStrategy.batches() == 0) {
                        connection.commit();
                    }
                }
            }

            ps.executeBatch();
            ps.clearBatch();

            if (manageTransaction) {
                // commit the final partial batch (and any whole batches not yet committed under EVERY_N_BATCHES)
                connection.commit();
            }
        } catch (SQLException e) {
            failure = e;
            if (manageTransaction) {
                // roll back, but never let a rollback failure replace the original cause
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
            }
        }

        // restore autocommit outside the try/finally so a restore failure can neither mask a primary
        // fill exception nor return a wrong-state connection to the pool (see restoreAutoCommit)
        if (manageTransaction) {
            failure = restoreAutoCommit(previousAutoCommit, failure);
        }

        if (failure != null) {
            // name the table: a driver's own message rarely does, and on a parallel fill the caller
            // cannot tell which of the concurrent tables failed. The original stays as the cause.
            throw new SQLException("failed to fill table [" + table.name() + "]: " + failure.getMessage(),
                    failure.getSQLState(), failure.getErrorCode(), failure);
        }

        tableWatch.stop();

        logger.debug("{}", tableWatch);

    }

    /**
     * Restores the connection's prior autocommit setting after an engine-managed transaction. A failure
     * here must neither replace a primary fill exception nor return a connection in an unknown
     * autocommit state to the pool, so on failure the connection is {@link Connection#abort aborted}
     * (the pool then discards it) and the restore failure is attached to {@code primary} when one
     * exists, or becomes the failure to throw otherwise.
     *
     * @param previousAutoCommit the autocommit value to restore
     * @param primary            the in-flight fill failure, or null if the fill succeeded
     * @return the exception the caller should throw (possibly {@code primary}), or null if none
     */
    private SQLException restoreAutoCommit(boolean previousAutoCommit, SQLException primary) {
        try {
            connection.setAutoCommit(previousAutoCommit);
            return primary;
        } catch (SQLException restoreFailure) {
            logger.error("failed to restore autocommit [{}] after fill; aborting the connection so it is "
                    + "not reused in an unknown transaction state", previousAutoCommit, restoreFailure);
            try {
                connection.abort(Runnable::run);
            } catch (SQLException abortFailure) {
                restoreFailure.addSuppressed(abortFailure);
            }
            if (primary != null) {
                primary.addSuppressed(restoreFailure);
                return primary;
            }
            return restoreFailure;
        }
    }

    /**
     * Positions every column generator so the next value produced is the one for absolute row
     * {@code startRow}, keeping a partitioned fill consistent with a sequential one:
     * <ul>
     *   <li><b>Positional generators</b> ({@link IndexedDataGenerator} — keys, sequences,
     *       permutations, prefixes) seek their counters directly to the absolute index, so they are
     *       byte-identical to the sequential fill regardless of partition count.</li>
     *   <li><b>Positionable columns</b> ({@link DataGenerator#positionable()}) need nothing further:
     *       the fill loop repositions their {@link IndexedRandom} to the absolute row index before
     *       every row, so seeking is O(1) and their values (foreign-key and plain random alike)
     *       are byte-identical to the sequential fill for any partition count.</li>
     *   <li><b>Legacy non-positionable foreign-key columns</b> ({@code maxInvocations[col] > 0})
     *       reseed and advance {@code startRow % maxInvocation} draws into the parent key cycle —
     *       the historical O(rows) replay, retained only for custom generators that opt out of
     *       positioning.</li>
     *   <li><b>Legacy non-positionable plain columns</b> are reseeded from a partition-derived seed;
     *       their values are deterministic for the chosen partition count but need not match a
     *       different partitioning, as they carry no cross-row contract.</li>
     * </ul>
     */
    private void seekGeneratorsTo(DataGenerator<?>[] generators, IndexedRandom[] positionables,
                                  long[] reseedSeeds, long[] maxInvocations, long startRow) {
        for (int col = 0; col < generators.length; col++) {
            DataGenerator<?> generator = generators[col];
            if (generator instanceof IndexedDataGenerator indexed) {
                indexed.seek(startRow);
            }
            if (positionables[col] != null || generator instanceof IndexedDataGenerator) {
                continue;
            }
            if (maxInvocations[col] > 0) {
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


    /**
     * Builder for constructing {@link TableFiller} instances.
     *
     * <p>Follows the builder pattern to assemble a filler for one table: the connection, database
     * metadata, and {@link DatabaseConfiguration} are required, while the target table, an optional
     * {@link CommitStrategy} override, and an optional row range are set fluently before
     * {@link #build()}. A {@code TableFiller} is typically created by {@link DatabaseFiller} once the
     * fill order has been determined.
     */
    public static class Builder {

        private final Connection connection;
        private final Database database;
        private final DatabaseConfiguration databaseConfiguration;

        private Table table;
        private CommitStrategy commitStrategy;
        private Map<String, ColumnConstraint> constraints;
        private boolean partitioned;
        private long rangeStartInclusive;
        private long rangeEndExclusive;

        /**
         * Creates a builder for a filler bound to the given connection, database metadata, and
         * configuration.
         *
         * @param connection the database connection to fill on
         * @param database the database metadata, used to resolve tables and foreign-key relationships
         * @param databaseConfiguration the configuration controlling batch size, row counts, seed, and commit behavior
         */
        public Builder(Connection connection, Database database, DatabaseConfiguration databaseConfiguration) {
            this.connection = Objects.requireNonNull(connection, "connection must not be null");
            this.database = Objects.requireNonNull(database, "database must not be null");
            this.databaseConfiguration = Objects.requireNonNull(databaseConfiguration, "databaseConfiguration must not be null");
        }

        /**
         * Sets the table to fill directly from its metadata.
         *
         * @param table the table to fill
         * @return this builder
         */
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
         * Supplies pre-resolved value-constraint metadata (CHECK / enum) for this table, keyed by
         * lower-cased column name, so the fill skips its own catalog read. When unset (or null),
         * constraints are read from the connection at fill time — the default behavior.
         * {@link DatabaseFiller} uses this to read a table's constraints once and share them
         * across intra-table partitions.
         *
         * @param constraints the constraint map to use, or null to read from the catalog
         * @return this builder
         */
        public Builder constraints(Map<String, ColumnConstraint> constraints) {
            this.constraints = constraints;
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

        /**
         * Builds a {@link TableFiller} from the configured parameters.
         *
         * @return a new filler ready to fill the configured table
         * @throws IllegalStateException if no table was configured via {@link #table(Table)}
         */
        public TableFiller build() {
            if (table == null) {
                throw new IllegalStateException("no table configured: call table(Table) before build()");
            }
            return new TableFiller(this);
        }
    }

    private TableFiller(Builder builder) {
        this.connection = builder.connection;
        this.table = builder.table;
        this.database = builder.database;
        this.databaseConfiguration = builder.databaseConfiguration;
        this.commitStrategy = builder.commitStrategy != null ? builder.commitStrategy : builder.databaseConfiguration.commitStrategy();
        this.constraints = builder.constraints;
        this.partitioned = builder.partitioned;
        this.rangeStartInclusive = builder.rangeStartInclusive;
        this.rangeEndExclusive = builder.rangeEndExclusive;
    }
}
