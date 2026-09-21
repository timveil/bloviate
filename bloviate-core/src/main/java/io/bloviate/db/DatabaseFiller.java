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

import io.bloviate.ext.BulkLoadHandle;
import io.bloviate.ext.BulkLoadUnsupportedException;
import io.bloviate.ext.DatabaseSupport;
import io.bloviate.util.DatabaseUtils;
import io.bloviate.util.JdbcUrls;
import org.apache.commons.lang3.time.StopWatch;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Main entry point for filling database tables with generated data.
 *
 * <p>The DatabaseFiller orchestrates the entire database filling process by:
 * <ul>
 *   <li>Analyzing database metadata to discover tables, columns, and relationships</li>
 *   <li>Building a dependency graph based on foreign key relationships</li>
 *   <li>Using topological sorting to determine the proper fill order</li>
 *   <li>Delegating individual table filling to {@link TableFiller} instances</li>
 * </ul>
 *
 * <p>The filling process respects foreign key constraints by ensuring parent tables
 * are populated before their dependent child tables. Self-referencing tables are
 * detected and logged as potential issues.
 *
 * <p>Ordered SQL hooks can run around the fill: {@link Builder#before(SqlScript) before} hooks run
 * before the schema is read and any table is filled, {@link Builder#after(SqlScript) after} hooks
 * once every table is filled (for example to compute a rollup from the generated rows). See
 * {@link SqlScriptRunner} for the script syntax and the transaction semantics.
 *
 * <p>By default every table of the connection's current schema is filled. {@link Builder#schema(String)}
 * and {@link Builder#catalog(String)} pick another schema, and {@link Builder#includeTables(String...)}
 * and {@link Builder#excludeTables(String...)} narrow the tables, for example to leave a derived table
 * to an after hook.
 *
 * <p>Relative date windows ({@link io.bloviate.gen.RelativeWindow}, "within the last 90 days") are
 * measured from one {@link Builder#asOf(Instant) asOf} anchor per fill, shared by every table, partition
 * and worker; pin it for output that is reproducible run to run.
 *
 * <p>Example usage:
 * <pre>{@code
 * DatabaseConfiguration config = new DatabaseConfiguration(batchSize, recordCount,
 *     databaseSupport, tableConfigs);
 * new DatabaseFiller.Builder(connection, config)
 *     .build()
 *     .fill();
 * }</pre>
 *
 * @author Tim Veil
 * @see TableFiller
 * @see DatabaseConfiguration
 * @see Fillable
 */
public class DatabaseFiller implements Fillable {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseFiller.class);

    /**
     * The commit cadence the parallel/bulk path uses when the caller leaves the commit strategy on
     * {@link CommitStrategy.Mode#CONNECTION_DEFAULT}: commit every this-many JDBC batches rather than
     * once per whole table/partition. This bounds the size of an open server-side transaction (WAL/undo
     * growth and lock accumulation) on a very large partition, which a single per-table commit would
     * not. See {@link #effectiveParallelCommitStrategy()}.
     */
    private static final int DEFAULT_PARALLEL_COMMIT_BATCHES = 64;

    /**
     * The largest DOT graph (in characters) for which {@link #visualizeGraph} renders a GraphvizOnline
     * link (at DEBUG, like the DOT itself — the link embeds every table name, so it stays out of
     * default-level logs). Beyond this the URL-encoded link (encoding can roughly triple the length)
     * is too long to be useful, so only the DOT is logged.
     */
    private static final int MAX_GRAPH_LINK_CHARS = 8_000;

    /** A caller-managed connection for the sequential path; null when filling from a {@link DataSource}. */
    private final Connection connection;

    /** A pooled data source for the parallel / self-managed path; null when a {@link Connection} was supplied. */
    private final DataSource dataSource;

    private final DatabaseConfiguration configuration;

    // per-fill cache of value-constraint metadata, keyed by table name; cleared at the start of
    // each fill() so a reused filler re-reads the catalog
    private final ConcurrentMap<String, Map<String, ColumnConstraint>> constraintCache = new ConcurrentHashMap<>();

    /** Worker threads for parallel table fill; {@code 1} (the default) keeps the fill sequential. */
    private final int threads;

    /** SQL scripts run, in order, before the schema is read and any table is filled. */
    private final List<SqlScript> beforeHooks;

    /** SQL scripts run, in order, after every table has been filled. */
    private final List<SqlScript> afterHooks;

    /** Which of the schema's tables are filled; {@link TableSelection#ALL} unless narrowed. */
    private final TableSelection tableSelection;

    /** The catalog/schema every connection the fill uses is pointed at; {@link SchemaSelection#NONE} leaves them alone. */
    private final SchemaSelection schemaSelection;

    /** The anchor for relative date windows when the caller pinned one; null to resolve it from {@link #clock}. */
    private final Instant asOf;

    /** Where an unpinned anchor is read from; the system UTC clock except in tests. */
    private final Clock clock;

    /**
     * The context of the current (or most recent) {@link #fill()}: created once when the fill starts and
     * handed to every {@link TableFiller}, whichever table, partition or worker thread it serves, so all
     * of them share one anchor. Null before the first fill of an unpinned filler.
     */
    private volatile GenerationContext generationContext;

    /**
     * Fills all tables in the database with generated data.
     *
     * <p>This method performs the complete database filling workflow:
     * <ol>
     *   <li>Retrieves database metadata including tables, columns, and foreign keys</li>
     *   <li>Builds a directed graph representing table dependencies</li>
     *   <li>Performs topological sorting to determine fill order</li>
     *   <li>Fills each table in dependency order using {@link TableFiller}</li>
     * </ol>
     *
     * <p>Progress and timing information is logged throughout the process.
     * A visualization link for the dependency graph is also provided in the logs.
     *
     * <p>Configured {@link Builder#before(SqlScript) before} hooks run first, ahead of the metadata
     * read, so they can create the schema being filled; {@link Builder#after(SqlScript) after} hooks
     * run once all tables are filled. A failing hook (or a failing fill, which skips the after hooks)
     * fails this method; tables filled before the failure stay filled, since there is no cross-table
     * rollback.
     *
     * <p>When a {@link Builder#schema(String) schema} or {@link Builder#catalog(String) catalog} is
     * selected it is applied to every connection the fill uses (hooks included) and undone afterwards:
     * on a supplied {@link Connection} when this method returns or throws, on a {@link DataSource}
     * connection before it goes back to the pool. Tables left out by
     * {@link Builder#includeTables(String...) includeTables}/{@link Builder#excludeTables(String...)
     * excludeTables} are not touched, and the fill fails before writing any row if a selected table
     * has a foreign key to one of them.
     *
     * <p>The {@link Builder#asOf(Instant) asOf} anchor of relative date windows is settled here, once,
     * before anything else runs: the pinned instant, or else the start of the current UTC day. Every
     * table, partition and worker of this call shares it.
     *
     * @throws SQLException if any database operation or hook script fails during the filling process,
     *                      or the driver cannot select the requested schema/catalog
     * @throws IllegalArgumentException if the table selection matches no table, or a selected table
     *                      references a table that is not selected
     */
    @Override
    public void fill() throws SQLException {
        generationContext = asOf != null ? GenerationContext.pinned(asOf) : GenerationContext.unpinned(clock);
        if (connection == null && threads <= 1) {
            // DataSource supplied but no parallelism requested: borrow ONE connection and use it for the
            // before hooks, the whole fill and the after hooks. Returning it in between would hand the
            // after hooks a different connection, and on a pool that hands out autoCommit=false
            // connections the fill's uncommitted rows (CommitStrategy.connectionDefault()) would already
            // have been rolled back when the fill connection went back to the pool.
            try (Connection conn = dataSource.getConnection()) {
                fill(conn);
            }
        } else {
            fill(connection);
        }
    }

    /**
     * Runs the fill. {@code sequentialConnection} is the single connection everything runs on (the
     * caller's own, or the one connection borrowed for a non-parallel {@link DataSource} fill), or
     * {@code null} for the parallel path, where the workers borrow their own connections.
     *
     * <p>On the single connection the selected schema/catalog is applied and restored per phase (before
     * hooks, the fill, after hooks) rather than once around the whole run. Between phases the
     * connection is therefore back on the caller's schema, and on a connection with autocommit off each
     * restore can be committed exactly when the phase's own commit made the selection durable; see
     * {@link #inSchema}.
     */
    private void fill(Connection sequentialConnection) throws SQLException {

        // before-hooks come first so they can create or empty the tables about to be filled, and so a
        // failing one prevents anything from being written
        runHooks("before", beforeHooks, sequentialConnection);

        // constraint metadata is per-fill state: a table's constraints are read once and shared
        // across its partitions/workers instead of once per partition
        constraintCache.clear();

        if (sequentialConnection == null) {
            // parallel path: the schema is applied to each connection as it is borrowed
            fillTables(null);
        } else {
            // engine-managed commit strategies commit inside the fill, which makes the selection durable
            inSchema(sequentialConnection, configuration.commitStrategy().managesTransaction(), () -> false,
                    () -> fillTables(sequentialConnection));
        }

        runHooks("after", afterHooks, sequentialConnection);
    }

    /**
     * The anchor that relative date windows are measured from: the instant pinned with
     * {@link Builder#asOf(Instant)}, or, for a filler without one, the instant the most recent
     * {@link #fill()} resolved (the start of the current UTC day when it began), so a caller can pin it to
     * reproduce that fill.
     *
     * @return the anchor, or empty if none was pinned and no fill has started yet
     * @since 3.7.0
     */
    public Optional<Instant> asOf() {
        if (asOf != null) {
            return Optional.of(asOf);
        }
        GenerationContext context = generationContext;
        return context == null ? Optional.empty() : Optional.of(context.peekAsOf());
    }

    /** A step run on a connection that is pointed at the selected schema; see {@link #inSchema}. */
    @FunctionalInterface
    private interface SchemaBody {
        void run() throws SQLException;
    }

    /**
     * Runs {@code body} on the single sequential connection with the selected schema/catalog applied,
     * and restores it afterwards, on success or failure.
     *
     * <p>Restoring can need a commit. On a database where {@code setSchema} is transactional
     * (PostgreSQL: {@code SET search_path}) and the connection has autocommit off, the selection and
     * its restore are part of the caller's open transaction. If something inside {@code body} committed
     * that transaction, the selection is durable but the restore is not, and a later rollback by the
     * caller would leave the connection on the selected schema. So when {@code body} is known to have
     * committed, and nothing but the restore can still be pending, the restore is committed too.
     *
     * @param commitAfterSuccess true if a successful {@code body} always ends with a commit, so the
     *                           restore is the only thing pending and can be committed
     * @param commitAfterFailure whether the same holds after a failed {@code body}, evaluated after the
     *                           failure
     */
    // UnusedLocalVariable: false positive. The schema scope is a try-with-resources resource that is
    // never read; closing it is the point (it restores the connection's schema/catalog).
    @SuppressWarnings("PMD.UnusedLocalVariable")
    private void inSchema(Connection conn, boolean commitAfterSuccess, BooleanSupplier commitAfterFailure,
                          SchemaBody body) throws SQLException {
        if (!schemaSelection.isSet()) {
            body.run();
            return;
        }
        try (SchemaSelection.Scope scope = schemaSelection.apply(conn, connection == null)) {
            body.run();
        } catch (SQLException | RuntimeException e) {
            if (commitAfterFailure.getAsBoolean()) {
                try {
                    commitIfManual(conn);
                } catch (SQLException commitFailure) {
                    e.addSuppressed(commitFailure);
                }
            }
            throw e;
        }
        if (commitAfterSuccess) {
            commitIfManual(conn);
        }
    }

    private static void commitIfManual(Connection conn) throws SQLException {
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    private void fillTables(Connection sequentialConnection) throws SQLException {

        StopWatch metadataWatch = new StopWatch("fetched database metadata in");
        metadataWatch.start();
        List<String> discoveredTableNames = new ArrayList<>();
        Map<String, String> partitions = new LinkedHashMap<>();
        Database database = readMetadata(sequentialConnection, discoveredTableNames, partitions);
        metadataWatch.stop();

        logger.debug("{}", metadataWatch);

        StopWatch databaseWatch = new StopWatch(String.format("filled database [%s] in", database.catalog()));
        databaseWatch.start();

        warnAboutUnusedTableConfigurations(database, discoveredTableNames, partitions);

        // fails before any row is written if a selected table references a table that is not selected
        Graph<Table, DefaultEdge> reversedGraph = buildReversedDependencyGraph(database);

        visualizeGraph(reversedGraph, database.catalog());

        warnIfEngineManagedCommitDiscouraged();

        // recommend the driver batch-rewrite URL parameter once per fill if it is missing
        if (sequentialConnection != null) {
            warnIfBatchRewriteMissing(sequentialConnection);
        } else {
            try (Connection conn = dataSource.getConnection()) {
                warnIfBatchRewriteMissing(conn);
            }
        }

        if (sequentialConnection != null) {
            // fill sequentially on the single connection: the caller's own (the back-compat path) or the
            // one borrowed from the DataSource when no parallelism was requested
            warnIfPartitionsIgnored();
            warnIfBulkIgnored();
            fillSequential(sequentialConnection, database, reversedGraph);
        } else {
            // parallel path: either the ordered level-by-level walk, or the unordered bulk path that
            // disables constraints and fills every table at once (when configured and supported)
            BulkLoadStrategy bulkLoadStrategy = configuration.bulkLoadStrategy();
            if (bulkLoadStrategy.isUnordered() && configuration.databaseSupport().supportsBulkLoad()) {
                fillUnordered(database, reversedGraph);
            } else {
                if (bulkLoadStrategy.isUnordered()) {
                    logger.warn("UNORDERED_BULK requested but {} does not support bulk load; using the ordered level-parallel path",
                            configuration.databaseSupport().getClass().getSimpleName());
                }
                fillParallel(database, reversedGraph);
            }
        }

        databaseWatch.stop();

        logger.info("{}", databaseWatch);
    }

    /**
     * Runs one phase's hook scripts, in order. On the sequential paths they run on the same connection
     * as the fill: the caller's own {@link Connection}, or the one connection borrowed from the
     * {@link DataSource} for the whole run, so session state a hook sets (a {@code SET search_path}, a
     * temporary table) carries into the fill and the other phase. On the parallel path
     * ({@code sequentialConnection == null}) each phase borrows a connection just for its hooks and
     * returns it before the workers start or after they finish: pinning one across the fill would
     * deadlock a pool sized to the thread count, and session state does not carry across phases.
     */
    // UnusedLocalVariable: false positive. The schema scope is a try-with-resources resource that is
    // never read; closing it is the point (it restores the connection's schema/catalog).
    @SuppressWarnings("PMD.UnusedLocalVariable")
    private void runHooks(String phase, List<SqlScript> hooks, Connection sequentialConnection) throws SQLException {
        if (hooks.isEmpty()) {
            return;
        }
        logger.info("running {} {} hook script(s)", hooks.size(), phase);
        if (sequentialConnection != null) {
            // a script that succeeded has committed (on a manual-commit connection), selection included;
            // a script that failed has rolled back only itself, so after a failure the restore is the
            // only thing pending exactly when an earlier script committed
            boolean[] committed = {false};
            inSchema(sequentialConnection, true, () -> committed[0], () -> {
                for (SqlScript script : hooks) {
                    SqlScriptRunner.run(sequentialConnection, script);
                    committed[0] = true;
                }
            });
        } else {
            try (Connection conn = dataSource.getConnection();
                 SchemaSelection.Scope scope = schemaSelection.apply(conn, true)) {
                SqlScriptRunner.runAll(conn, hooks);
            }
        }
    }

    /**
     * Reads the metadata of the selected tables: on the single connection of a sequential fill (already
     * pointed at the selected schema), otherwise on a connection borrowed from the pool for the read,
     * pointed at the selected schema and restored before it goes back.
     */
    // UnusedLocalVariable: false positive. The schema scope is a try-with-resources resource that is
    // never read; closing it is the point (it restores the connection's schema/catalog).
    @SuppressWarnings("PMD.UnusedLocalVariable")
    private Database readMetadata(Connection sequentialConnection, List<String> discoveredTableNames,
                                  Map<String, String> partitions) throws SQLException {
        // the selection sees every table name of the schema (a partition of a partitioned table is not
        // one: it is filled through its parent); keep a copy of that full list, and of the partitions,
        // before the selection narrows it, so table configurations can be classified against what
        // really exists
        BiFunction<List<String>, Map<String, String>, List<String>> filter = (names, found) -> {
            discoveredTableNames.addAll(names);
            partitions.putAll(found);
            return tableSelection.select(names, found);
        };
        DatabaseSupport support = configuration.databaseSupport();
        if (sequentialConnection != null) {
            return DatabaseUtils.getMetadata(sequentialConnection, support, filter);
        }
        try (Connection conn = dataSource.getConnection();
             SchemaSelection.Scope scope = schemaSelection.apply(conn, true)) {
            return DatabaseUtils.getMetadata(conn, support, filter);
        }
    }

    /**
     * Logs one warning naming the {@link TableConfiguration}s that configure a table that will not be
     * filled: those matching no table at all (usually a typo), and those matching a table the table
     * selection left out (the configuration has no effect). Neither is an error here; nothing else
     * about them changes.
     */
    private void warnAboutUnusedTableConfigurations(Database database, List<String> discoveredTableNames,
                                                    Map<String, String> partitions) {
        UnusedTableConfigurations unused = findUnusedTableConfigurations(database, discoveredTableNames, partitions, configuration);
        if (!unused.unknown().isEmpty()) {
            logger.warn("table configuration(s) for {} match no table in the selected schema and are ignored",
                    unused.unknown());
        }
        if (!unused.excluded().isEmpty()) {
            logger.warn("table configuration(s) for {} are ignored because includeTables/excludeTables leave those tables out",
                    unused.excluded());
        }
        unused.partitions().forEach((name, root) -> logger.warn(
                "table configuration for [{}] is ignored: it is a partition of [{}], and a partitioned table is filled "
                        + "through its parent; configure [{}] instead", name, root, root));
    }

    /**
     * The names in a configuration's table configurations that will not be filled.
     *
     * @param unknown  names matching no table of the selected schema
     * @param excluded names of tables the table selection left out
     * @param partitions names of partitions of a partitioned table (never filled on their own), each mapped
     *                   to the partitioned table to configure instead
     */
    record UnusedTableConfigurations(List<String> unknown, List<String> excluded, Map<String, String> partitions) {
    }

    /**
     * Sorts the names of {@code configuration}'s table configurations that match no table of
     * {@code database} into typos ({@code unknown}: no such table in the schema at all) and tables the
     * selection left out ({@code excluded}: the table exists in the schema, as {@code discoveredTableNames}
     * lists them before any selection was applied, but is not in {@code database}). Both lists are
     * sorted, so the warning does not depend on the configuration set's iteration order.
     */
    static UnusedTableConfigurations findUnusedTableConfigurations(Database database, List<String> discoveredTableNames,
                                                                   DatabaseConfiguration configuration) {
        return findUnusedTableConfigurations(database, discoveredTableNames, Map.of(), configuration);
    }

    /**
     * As {@link #findUnusedTableConfigurations(Database, List, DatabaseConfiguration)}, also telling the
     * configurations that name a partition of a partitioned table ({@code partitions}: partition name to its
     * top-level partitioned table) from typos: such a table exists, but is filled through its parent.
     */
    static UnusedTableConfigurations findUnusedTableConfigurations(Database database, List<String> discoveredTableNames,
                                                                   Map<String, String> partitions,
                                                                   DatabaseConfiguration configuration) {
        List<String> unknown = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        Map<String, String> partitioned = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<TableConfiguration> tableConfigurations = configuration.tableConfigurations();
        if (tableConfigurations != null) {
            for (TableConfiguration tableConfiguration : tableConfigurations) {
                String name = tableConfiguration.tableName();
                if (database.findTable(name).isPresent()) {
                    continue;
                }
                Optional<String> partitionRoot = partitions.entrySet().stream()
                        .filter(partition -> partition.getKey().equalsIgnoreCase(name))
                        .map(Map.Entry::getValue)
                        .findFirst();
                if (partitionRoot.isPresent()) {
                    partitioned.put(name, partitionRoot.get());
                } else if (discoveredTableNames.stream().anyMatch(discovered -> discovered.equalsIgnoreCase(name))) {
                    excluded.add(name);
                } else {
                    unknown.add(name);
                }
            }
        }
        unknown.sort(String.CASE_INSENSITIVE_ORDER);
        excluded.sort(String.CASE_INSENSITIVE_ORDER);
        return new UnusedTableConfigurations(unknown, excluded, partitioned);
    }

    /**
     * Fills every table on a single connection in dependency order — the original, default
     * behavior. Parent (referenced) tables are filled before the tables that depend on them.
     *
     * @param conn         the connection to fill on
     * @param database     the database metadata
     * @param reversedGraph the reversed dependency graph (parents before children)
     * @throws SQLException if any table fill fails
     */
    private void fillSequential(Connection conn, Database database, Graph<Table, DefaultEdge> reversedGraph) throws SQLException {
        requireAcyclic(reversedGraph);

        TopologicalOrderIterator<Table, DefaultEdge> iterator = new TopologicalOrderIterator<>(reversedGraph);
        while (iterator.hasNext()) {
            new TableFiller.Builder(conn, database, configuration)
                    .table(iterator.next())
                    .generationContext(generationContext)
                    .build().fill();
        }
    }

    /**
     * Fills tables concurrently, one topological level at a time. All tables within a level are
     * independent (none references another in the same level), so they can fill in parallel; the
     * walk barriers between levels so a child table is never filled before its parent is committed.
     *
     * <p>Each worker borrows its own {@link Connection} from the {@link DataSource}, fills a single
     * table inside an explicit transaction (autocommit off, one commit per table), and returns the
     * connection to the pool. JDBC connections are not thread-safe, so they are never shared.
     *
     * <p>Reproducibility is preserved: a table's generated data depends only on its own per-column
     * seeds and its own sequential row counter, never on the order in which tables are filled, so for
     * the same seed a parallel fill yields the same row content as the sequential fill across every
     * deterministic column (physical row order and non-deterministic columns aside; see
     * {@link BulkLoadStrategy}).
     *
     * @param database     the database metadata
     * @param reversedGraph the reversed dependency graph (parents before children)
     * @throws SQLException if any table fill fails or the run is interrupted
     */
    private void fillParallel(Database database, Graph<Table, DefaultEdge> reversedGraph) throws SQLException {
        requireAcyclic(reversedGraph);

        List<List<Table>> levels = fillLevels(reversedGraph);

        // never spin up more workers than the widest level can use; a partitioned table contributes
        // one unit of work per partition, so a single large partitioned table can use all threads
        int widest = levels.stream()
                .mapToInt(level -> level.stream().mapToInt(this::partitionsFor).sum())
                .max().orElse(1);
        int poolSize = Math.clamp(threads, 1, widest);

        logger.info("filling {} tables across {} topological level(s) with {} worker thread(s)",
                reversedGraph.vertexSet().size(), levels.size(), poolSize);

        // try-with-resources: ExecutorService#close() shuts the pool down and awaits termination
        // (and shutdownNow()s on interrupt), so the pool is always cleanly torn down
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            for (List<Table> level : levels) {
                List<Callable<Void>> tasks = new ArrayList<>(level.size());
                for (Table table : level) {
                    addTableTasks(tasks, database, table, false);
                }

                // barrier: every table in this level must finish before the next level may start.
                // Submitted with backpressure so queued tasks stay bounded even for a level whose
                // tables carry a large partition count (see runWithBackpressure).
                runWithBackpressure(executor, tasks, poolSize);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("parallel table fill was interrupted", e);
        }
    }

    /**
     * Fills every table concurrently with foreign-key enforcement disabled and <strong>no</strong>
     * topological barrier — the {@link BulkLoadStrategy#unorderedBulk()} path. Each worker borrows its
     * own connection, disables constraints on that session (see
     * {@link DatabaseSupport#disableConstraints}), fills its table/partition, and restores enforcement
     * in a {@code finally} before the connection returns to the pool. Collapsing all topological levels
     * into a single wave removes the serialization cost of deep, narrow dependency chains.
     *
     * <p>This is only correct because Bloviate's data is referentially consistent by construction (a
     * foreign-key column is seeded from its referenced primary-key column), so insert order does not
     * affect validity and, for the same seed, the result has the same row content as an ordered fill
     * across every deterministic column (physical row order and non-deterministic columns aside; see
     * {@link BulkLoadStrategy}).
     *
     * <p>Privilege is probed once up front on a throwaway connection; if constraints cannot be disabled
     * (e.g. the role lacks privilege for {@code session_replication_role}), the engine logs a warning
     * and falls back to {@link #fillParallel} rather than risk a half-disabled run.
     *
     * @param database the database metadata
     * @param graph    the reversed dependency graph (used only for its vertex set here)
     * @throws SQLException if any table fill fails or the run is interrupted
     */
    // UnusedLocalVariable: false positive. The schema scope is a try-with-resources resource that is
    // never read; closing it is the point (it restores the connection's schema/catalog).
    @SuppressWarnings("PMD.UnusedLocalVariable")
    private void fillUnordered(Database database, Graph<Table, DefaultEdge> graph) throws SQLException {
        DatabaseSupport support = configuration.databaseSupport();

        // probe once: if we can't disable+re-enable constraints on a borrowed connection, fall back to
        // the ordered path instead of fanning out into a partially-disabled state. As in the worker
        // bodies, a failed re-enable aborts the connection rather than returning it to the pool still
        // in its constraint-disabled state (see restoreConstraints).
        try (Connection conn = dataSource.getConnection();
             SchemaSelection.Scope scope = schemaSelection.apply(conn, true)) {
            BulkLoadHandle handle = support.disableConstraints(conn, database);
            restoreConstraints(support, conn, database, handle);
        } catch (BulkLoadUnsupportedException e) {
            logger.warn("UNORDERED_BULK requested but constraints could not be disabled ({}); "
                    + "falling back to the ordered level-parallel path", e.getMessage());
            fillParallel(database, graph);
            return;
        }

        // one wave: every table (and partition) becomes a task with no topological barrier; the
        // worker bodies disable/restore constraints per connection (see fillTableInOwnTransaction)
        List<Callable<Void>> tasks = new ArrayList<>();
        for (Table table : graph.vertexSet()) {
            addTableTasks(tasks, database, table, true);
        }

        int poolSize = Math.clamp(threads, 1, Math.max(1, tasks.size()));

        logger.info("bulk-filling {} table(s) with constraints disabled across {} worker thread(s) (no topological barrier)",
                graph.vertexSet().size(), poolSize);

        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            // submitted with backpressure so queued tasks stay bounded even when the schema (or a
            // partitioned table) produces far more tasks than worker threads (see runWithBackpressure)
            runWithBackpressure(executor, tasks, poolSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("bulk table fill was interrupted", e);
        }
    }

    /**
     * Logs a one-time recommendation to enable the driver's batch-rewrite URL parameter when the
     * connection's URL does not already set it to {@code true}. Bloviate fills through a connection
     * it does not own, so it cannot add the parameter itself; enabling it (e.g. with
     * {@link io.bloviate.util.JdbcUrls#withBatchRewrite(String, String)}) is often the single biggest
     * fill speedup. No-op for databases without such a parameter (e.g. CockroachDB).
     *
     * @param conn a connection whose URL is inspected; never modified
     * @throws SQLException if the connection metadata cannot be read
     */
    private void warnIfBatchRewriteMissing(Connection conn) throws SQLException {
        String parameter = configuration.databaseSupport().batchRewriteUrlParameter();
        if (parameter == null) {
            return;
        }
        String url = conn.getMetaData().getURL();
        if (url != null && JdbcUrls.parameterEquals(url, parameter, "true")) {
            return;
        }
        logger.warn("JDBC driver batch rewrite is not enabled; add '{}=true' to the JDBC URL for a "
                + "potentially large fill speedup (see io.bloviate.util.JdbcUrls#withBatchRewrite)", parameter);
    }

    /**
     * Runs every task on {@code executor} but keeps at most {@code ~2 × poolSize} in flight at once,
     * draining a completed task before submitting the next. This bounds the executor's work queue (and
     * the outstanding {@link Future}s) to a small multiple of the pool size rather than letting them
     * scale with the total task count — important when a large {@code partitions} value (or many
     * partitioned tables) produces far more tasks than worker threads, which an eager
     * {@code invokeAll(allTasks)} would queue all at once. All tasks still complete before this returns,
     * preserving the level barrier in {@link #fillParallel}. The first task failure is rethrown; the
     * surrounding {@code try-with-resources} on the executor then drains the in-flight tasks on close.
     */
    // ForLoopCanBeForeach: false positive. The loop counts completions, it does not iterate tasks --
    // the body indexes with `next`, not the loop variable, so a foreach would change what it submits.
    @SuppressWarnings("PMD.ForLoopCanBeForeach")
    private void runWithBackpressure(ExecutorService executor, List<Callable<Void>> tasks, int poolSize) throws SQLException, InterruptedException {
        CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);
        int inFlightCap = Math.max(1, 2 * poolSize);
        int next = 0;

        // prime the pipeline up to the in-flight cap
        while (next < tasks.size() && next < inFlightCap) {
            completionService.submit(tasks.get(next++));
        }

        // for each completion, surface any failure and top the pipeline back up
        for (int completed = 0; completed < tasks.size(); completed++) {
            awaitFuture(completionService.take());
            if (next < tasks.size()) {
                completionService.submit(tasks.get(next++));
            }
        }
    }

    /** Unwraps a worker future, re-throwing the underlying {@link SQLException} or runtime failure. */
    // PreserveStackTrace: unwrapping is the point. ExecutionException is a transport wrapper added by
    // the executor; the cause carries the worker thread's own stack trace, so rethrowing it directly
    // gives a cleaner trace than re-wrapping. The fallback branch does chain the cause.
    @SuppressWarnings("PMD.PreserveStackTrace")
    private void awaitFuture(Future<Void> future) throws SQLException {
        try {
            future.get();
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case SQLException sqlException -> throw sqlException;
                case RuntimeException runtimeException -> throw runtimeException;
                case null, default -> throw new SQLException("parallel table fill failed", e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("parallel table fill was interrupted", e);
        }
    }

    /**
     * Adds the worker task(s) for one table to {@code tasks}. A table with the default single
     * partition contributes one whole-table task; a table configured with {@code partitions > 1}
     * contributes one task per contiguous row range, so the ranges fill concurrently (intra-table
     * parallelism). When {@code bulk} is true the worker disables foreign-key enforcement on its
     * connection for the duration of the fill (the unordered bulk path); otherwise it fills with
     * enforcement intact (the ordered level-parallel path).
     */
    private void addTableTasks(List<Callable<Void>> tasks, Database database, Table table, boolean bulk) {
        int partitions = partitionsFor(table);
        if (partitions <= 1) {
            tasks.add(() -> {
                fillTableInOwnTransaction(database, table, bulk);
                return null;
            });
            return;
        }

        long rowCount = rowCountFor(table);
        long base = rowCount / partitions;
        long remainder = rowCount % partitions;
        long start = 0;
        for (int p = 0; p < partitions; p++) {
            long size = base + (p < remainder ? 1 : 0);
            long end = start + size;
            if (size > 0) {
                long rangeStart = start;
                tasks.add(() -> {
                    fillTablePartition(database, table, rangeStart, end, bulk);
                    return null;
                });
            }
            start = end;
        }
    }

    /**
     * Fills a whole table on its own pooled connection inside a single transaction (no row range, so
     * each generator is driven through the same call sequence as a sequential fill, producing identical
     * values for every deterministic column). The transaction is managed by
     * {@link TableFiller} via the effective commit strategy (see {@link #effectiveParallelCommitStrategy()}).
     * When {@code bulk} is true, foreign-key enforcement is disabled for the fill and restored before
     * the connection returns to the pool.
     */
    private void fillTableInOwnTransaction(Database database, Table table, boolean bulk) throws SQLException {
        fillOnPooledConnection(database, bulk, conn ->
                new TableFiller.Builder(conn, database, configuration)
                        .table(table)
                        .constraints(constraintsFor(conn, table))
                        .commitStrategy(effectiveParallelCommitStrategy())
                        .generationContext(generationContext)
                        .build().fill());
    }

    /**
     * Fills one contiguous row range of a table on its own pooled connection inside a single
     * transaction. The transaction is managed by {@link TableFiller} via the effective commit
     * strategy (see {@link #effectiveParallelCommitStrategy()}): autocommit off, committed when the
     * range is complete (or every N batches), and rolled back on failure. When {@code bulk} is true,
     * foreign-key enforcement is disabled for the fill and restored before the connection returns to
     * the pool.
     */
    private void fillTablePartition(Database database, Table table, long startInclusive, long endExclusive, boolean bulk) throws SQLException {
        fillOnPooledConnection(database, bulk, conn ->
                new TableFiller.Builder(conn, database, configuration)
                        .table(table)
                        .constraints(constraintsFor(conn, table))
                        .commitStrategy(effectiveParallelCommitStrategy())
                        .rowRange(startInclusive, endExclusive)
                        .generationContext(generationContext)
                        .build().fill());
    }

    /**
     * Returns the value-constraint metadata for {@code table}, reading it from the catalog at most
     * once per {@link #fill()} run — an intra-table parallel fill would otherwise issue the same
     * catalog queries once per partition.
     */
    private Map<String, ColumnConstraint> constraintsFor(Connection conn, Table table) {
        List<Column> columns = table.filteredColumns();
        String schema = columns.isEmpty() ? null : columns.getFirst().schema();
        // schema-qualified key: constraints are schema-sensitive and one fill can see
        // same-named tables in different schemas (metadata is loaded with a null schema
        // filter on connections without a current schema)
        String cacheKey = schema == null ? table.name() : schema + '.' + table.name();
        return constraintCache.computeIfAbsent(cacheKey,
                key -> configuration.databaseSupport().readConstraints(conn, schema, table.name()));
    }

    /**
     * Borrows a connection from the pool and runs {@code body} on it. When {@code bulk} is true,
     * foreign-key enforcement is disabled on the connection's session before {@code body} and restored
     * in a {@code finally} <em>before</em> the connection returns to the pool — so a constraint-disabled
     * connection never leaks to other pool users, even if the fill throws. If the restore itself fails,
     * the connection is aborted rather than returned to the pool (see {@link #restoreConstraints}). The
     * disable/enable mechanism is database-specific (see {@link DatabaseSupport#disableConstraints}).
     */
    // UnusedLocalVariable: false positive. The schema scope is a try-with-resources resource that is
    // never read; closing it is the point (it restores the connection's schema/catalog).
    @SuppressWarnings("PMD.UnusedLocalVariable")
    private void fillOnPooledConnection(Database database, boolean bulk, ConnectionFill body) throws SQLException {
        DatabaseSupport support = configuration.databaseSupport();
        // the scope closes before the connection returns to the pool (resources close in reverse order)
        try (Connection conn = dataSource.getConnection();
             SchemaSelection.Scope scope = schemaSelection.apply(conn, true)) {
            BulkLoadHandle handle = bulk ? support.disableConstraints(conn, database) : null;
            try {
                body.fill(conn);
            } finally {
                if (bulk) {
                    restoreConstraints(support, conn, database, handle);
                }
            }
        }
    }

    /**
     * Re-enables the foreign-key enforcement that {@link DatabaseSupport#disableConstraints} turned off
     * on {@code conn}. If the restore itself fails, the connection is still in its constraint-disabled
     * session state (e.g. PostgreSQL {@code session_replication_role=replica}); connection pools do not
     * reset arbitrary session variables on return, so returning it would silently skip FK enforcement
     * for whatever borrows it next. To prevent that, the connection is {@link Connection#abort aborted}
     * so the pool discards the physical connection instead of reusing it, and the failure is rethrown
     * loudly rather than swallowed.
     */
    private void restoreConstraints(DatabaseSupport support, Connection conn, Database database, BulkLoadHandle handle) throws SQLException {
        try {
            support.enableConstraints(conn, database, handle);
        } catch (SQLException e) {
            logger.error("failed to re-enable constraints [{}] on a bulk-load connection; aborting it so the "
                    + "constraint-disabled session is not returned to the pool", handle, e);
            try {
                conn.abort(Runnable::run);
            } catch (SQLException abortFailure) {
                e.addSuppressed(abortFailure);
            }
            throw e;
        }
    }

    /** A fill action against a borrowed connection; see {@link #fillOnPooledConnection}. */
    @FunctionalInterface
    private interface ConnectionFill {
        void fill(Connection connection) throws SQLException;
    }

    /** The configured intra-table partition count for a table, or {@code 1} when not configured. */
    private int partitionsFor(Table table) {
        TableConfiguration tableConfiguration = configuration.tableConfiguration(table.name());
        return tableConfiguration != null ? tableConfiguration.partitions() : 1;
    }

    /** The row count for a table: its per-table override if present, otherwise the default. */
    private long rowCountFor(Table table) {
        TableConfiguration tableConfiguration = configuration.tableConfiguration(table.name());
        return tableConfiguration != null ? tableConfiguration.rowCount() : configuration.defaultRowCount();
    }

    /**
     * Logs a warning for any table configured with intra-table {@code partitions > 1} when the fill
     * will not run on the parallel path, since partitioning has no effect there (mirrors the
     * {@code threads(...)} warning). Intra-table parallelism requires the {@link DataSource}
     * constructor with {@code threads > 1}.
     */
    private void warnIfPartitionsIgnored() {
        Set<TableConfiguration> tableConfigurations = configuration.tableConfigurations();
        if (tableConfigurations == null) {
            return;
        }
        for (TableConfiguration tableConfiguration : tableConfigurations) {
            if (tableConfiguration.partitions() > 1) {
                logger.warn("intra-table partitions ({}) for table [{}] are ignored on the sequential fill path; "
                                + "use the DataSource constructor with threads(n) > 1 for intra-table parallelism",
                        tableConfiguration.partitions(), tableConfiguration.tableName());
            }
        }
    }

    /**
     * Logs a warning when an {@link BulkLoadStrategy#unorderedBulk()} fill was requested but the fill
     * will not run on the parallel path (single connection, or a {@link DataSource} with one thread).
     * Bulk loading needs per-worker session control, so it only applies to the {@code threads > 1}
     * {@link DataSource} path; elsewhere the engine fills in dependency order.
     */
    private void warnIfBulkIgnored() {
        if (configuration.bulkLoadStrategy().isUnordered()) {
            logger.warn("UNORDERED_BULK is ignored on the sequential fill path; use the DataSource "
                    + "constructor with threads(n) > 1 for unordered bulk loading");
        }
    }

    /**
     * Warns once per fill when an explicit commit strategy is configured against a support that
     * would rather the engine stayed out of transaction management. The caller's choice is still
     * honored — this only surfaces the cost, which is otherwise invisible (on BigQuery, an engine-
     * managed transaction opens a session and silently disables the driver's load-job path).
     */
    private void warnIfEngineManagedCommitDiscouraged() {
        if (configuration.databaseSupport().prefersConnectionDefaultCommit()
                && configuration.commitStrategy().managesTransaction()) {
            logger.warn("{} recommends leaving transaction management to the connection, but commit "
                            + "strategy [{}] was configured; the engine will manage transactions as asked, "
                            + "which may be slower and can disable driver bulk-load paths",
                    configuration.databaseSupport().getClass().getSimpleName(),
                    configuration.commitStrategy().mode());
        }
    }

    /**
     * The commit strategy used by parallel workers. A pooled worker connection must not be left on
     * the connection's autocommit (that would commit per batch and lose the engine-managed
     * transaction), so {@link CommitStrategy.Mode#CONNECTION_DEFAULT} maps to a bounded
     * {@link CommitStrategy#everyNBatches(int)} of {@value #DEFAULT_PARALLEL_COMMIT_BATCHES} batches
     * rather than {@link CommitStrategy#perTable()}: a single per-table commit would hold an entire
     * large partition open in one server-side transaction (unbounded WAL/undo growth and lock
     * accumulation), which is the scale failure the parallel/bulk path most needs to avoid. Any
     * explicitly configured strategy (including {@link CommitStrategy#perTable()}) is honored as-is.
     *
     * <p>A {@link io.bloviate.ext.DatabaseSupport#prefersConnectionDefaultCommit() support that
     * prefers the connection's own commit behavior} suppresses the upgrade, so
     * {@code CONNECTION_DEFAULT} stays as configured. That is for engines where an engine-managed
     * transaction is pure cost rather than protection — see the hook's documentation.
     *
     * <p>Package-private so the mapping can be unit-tested without a database.
     */
    CommitStrategy effectiveParallelCommitStrategy() {
        CommitStrategy configured = configuration.commitStrategy();
        if (configured.mode() != CommitStrategy.Mode.CONNECTION_DEFAULT) {
            return configured;
        }
        return configuration.databaseSupport().prefersConnectionDefaultCommit()
                ? configured
                : CommitStrategy.everyNBatches(DEFAULT_PARALLEL_COMMIT_BATCHES);
    }

    /**
     * Partitions the dependency graph into topological levels via Kahn's algorithm: level 0 holds
     * every table that references nothing, level 1 the tables whose parents are all in level 0, and
     * so on. Tables in the same level are mutually independent and therefore safe to fill in
     * parallel. Iteration order of the graph's vertex set is preserved, so levels are deterministic.
     *
     * <p>If the graph contains a cycle (e.g. mutually referencing tables), the tables in the cycle
     * never reach in-degree zero and are omitted from the levels — matching the sequential
     * {@link TopologicalOrderIterator}, which likewise cannot order a cycle. A fill never reaches
     * that state: {@link #requireAcyclic} rejects a cyclic graph before this runs, because silently
     * returning fewer tables than were asked for is worse than failing (issue #618). The warning
     * below remains for a direct caller.
     *
     * @param graph the reversed dependency graph (an edge points from a parent to a child)
     * @return the tables grouped into dependency-respecting levels, parents before children
     */
    List<List<Table>> fillLevels(Graph<Table, DefaultEdge> graph) {
        Map<Table, Integer> inDegree = new HashMap<>();
        for (Table table : graph.vertexSet()) {
            inDegree.put(table, graph.inDegreeOf(table));
        }

        List<Table> current = new ArrayList<>();
        for (Table table : graph.vertexSet()) {
            if (inDegree.get(table) == 0) {
                current.add(table);
            }
        }

        List<List<Table>> levels = new ArrayList<>();
        int ordered = 0;
        while (!current.isEmpty()) {
            levels.add(current);
            List<Table> next = new ArrayList<>();
            for (Table table : current) {
                ordered++;
                for (DefaultEdge edge : graph.outgoingEdgesOf(table)) {
                    Table child = graph.getEdgeTarget(edge);
                    int remaining = inDegree.get(child) - 1;
                    inDegree.put(child, remaining);
                    if (remaining == 0) {
                        next.add(child);
                    }
                }
            }
            current = next;
        }

        if (ordered != graph.vertexSet().size()) {
            logger.warn("dependency graph contains a cycle; {} of {} table(s) could not be ordered and will not be filled",
                    graph.vertexSet().size() - ordered, graph.vertexSet().size());
        }

        return levels;
    }

    /**
     * Builds the table dependency graph used to determine fill order.
     *
     * <p>An edge is added from each table to every table it references through a foreign
     * key, then the graph is reversed so that a topological traversal yields referenced
     * (parent) tables before the tables that depend on them. Self-referencing tables are
     * logged as likely problematic.
     *
     * @param database the database whose tables and foreign keys define the dependencies
     * @return the reversed dependency graph, ready for topological ordering
     * @throws IllegalArgumentException if a table has a foreign key to a table that is not in
     *                                  {@code database}; see {@link #requireForeignKeyTargetsPresent}
     */
    static Graph<Table, DefaultEdge> buildReversedDependencyGraph(Database database) {
        requireForeignKeyTargetsPresent(database);

        Graph<Table, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (Table table : database.tables()) {

            List<ForeignKey> foreignKeys = table.foreignKeys();

            graph.addVertex(table);

            if (foreignKeys != null && !foreignKeys.isEmpty()) {
                for (ForeignKey key : foreignKeys) {
                    Table referencedTable = database.getTable(key.primaryKey().tableName());
                    if (!graph.containsVertex(referencedTable)) {
                        graph.addVertex(referencedTable);
                    }

                    if (table.equals(referencedTable)) {
                        // a self-referencing foreign key adds no inter-table ordering constraint; skip
                        // the self-edge (a self-loop would also be rejected by the DAG) and warn so the
                        // user knows intra-table parent/child ordering is their responsibility
                        logger.warn("table [{}] has a self-referencing foreign key on column(s) {}; it imposes no "
                                + "fill order and rows may reference not-yet-inserted parents",
                                table.name(), key.foreignKeyColumns());
                        continue;
                    }

                    try {
                        graph.addEdge(table, referencedTable);
                    } catch (IllegalArgumentException e) {
                        logger.error("could not add dependency edge from [{}] to [{}]: {}",
                                table.name(), referencedTable.name(), e.getMessage(), e);
                    }
                }
            }
        }

        return new EdgeReversedGraph<>(graph);
    }

    /**
     * Fails if the dependency graph contains a cycle &mdash; two or more tables that reference each
     * other, directly or through a chain.
     *
     * <p>No order fills such a schema: whichever table goes first, its foreign key points at rows that
     * do not exist yet. The ordered paths used to disagree about this. The sequential one let a raw
     * {@code NotDirectedAcyclicGraphException} out of the topological iterator, and the level-parallel
     * one logged a warning and <strong>filled neither table</strong>, so a fill reported success having
     * silently left them empty (issue #618). Both now fail here instead, before a row is written, with
     * every cycle named.
     *
     * <p>{@link BulkLoadStrategy#unorderedBulk()} is the exception and keeps working: it disables
     * constraint enforcement and fills every table at once, so it needs no order and has no cycle to
     * break. That is what the message points at &mdash; qualified, because it is only available on a
     * {@link javax.sql.DataSource} and where {@link io.bloviate.ext.DatabaseSupport#supportsBulkLoad()}
     * is true. Elsewhere it falls back to the level-parallel path and lands back here, so the message
     * says so rather than sending an H2 or SQLite user round the same loop.
     *
     * <p>A table referencing itself is not a cycle here: {@link #buildReversedDependencyGraph} leaves
     * the self-edge out (and warns), because a self-reference constrains the order of rows within one
     * table rather than the order of tables.
     *
     * @param graph the reversed dependency graph
     * @throws IllegalArgumentException if any set of tables is mutually dependent
     */
    static void requireAcyclic(Graph<Table, DefaultEdge> graph) {
        List<String> cycles = new KosarajuStrongConnectivityInspector<>(graph).stronglyConnectedSets().stream()
                // a component of one vertex is a plain table; only a larger one is a cycle
                .filter(component -> component.size() > 1)
                .map(component -> component.stream().map(Table::name).sorted().toList().toString())
                .sorted()
                .toList();

        if (cycles.isEmpty()) {
            return;
        }

        throw new IllegalArgumentException("cannot fill: the tables " + String.join(" and ", cycles)
                + " reference each other, so no fill order satisfies them: whichever is filled first, its "
                + "foreign key has no parent row to point at yet. Break the cycle in the schema, leave every table "
                + "of it out with excludeTables (dropping only one leaves the others referencing a table that is "
                + "not being filled), or, on a DataSource and a database whose support implements bulk loading "
                + "(PostgreSQL, MySQL, MariaDB), fill with BulkLoadStrategy.unorderedBulk(), which disables "
                + "constraint enforcement and needs no order. Nothing was written.");
    }

    /**
     * Fails if any table has a foreign key to a table that is not in {@code database}: the referencing
     * table's values are seeded from the parent's primary key, and the parent must be filled first, so a
     * parent left out of the selection (or living in another schema) cannot be honoured. Reported for
     * every offending key at once, before anything is written, naming the child table, the foreign-key
     * column(s) and the missing parent. A table referencing itself is fine.
     *
     * @param database the selected tables
     * @throws IllegalArgumentException if a foreign key's parent is not among {@code database}'s tables
     */
    static void requireForeignKeyTargetsPresent(Database database) {
        List<String> problems = new ArrayList<>();
        boolean otherSchema = false;
        for (Table table : database.tables()) {
            List<ForeignKey> foreignKeys = table.foreignKeys();
            if (foreignKeys == null) {
                continue;
            }
            for (ForeignKey key : foreignKeys) {
                String parent = key.primaryKey().tableName();
                // a parent in another schema/catalog is missing even if this schema has a table of the same
                // name: the foreign key does not reference that one
                if (key.referencesOtherSchema() || database.findTable(parent).isEmpty()) {
                    otherSchema |= key.referencesOtherSchema();
                    problems.add(String.format("table [%s] foreign key on column(s) %s references table [%s]%s",
                            table.name(), key.foreignKeyColumns().stream().map(keyColumn -> keyColumn.column().name()).toList(), parent,
                            qualifier(key)));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("cannot fill: " + String.join("; ", problems)
                    + ", which is not among the tables being filled (left out by includeTables/excludeTables, or in another schema). "
                    + "Add the referenced table(s) to includeTables (or remove the excludeTables pattern that drops them), "
                    + (otherSchema ? "(a table in another schema cannot be included) " : "")
                    + "or exclude the referencing table(s) as well. Nothing was written.");
        }
    }

    /** " in schema [s] catalog [c]" for a foreign key into another schema/catalog, otherwise empty. */
    private static String qualifier(ForeignKey key) {
        StringBuilder where = new StringBuilder();
        if (key.referencedSchema() != null) {
            where.append(" in schema [").append(key.referencedSchema()).append(']');
        }
        if (key.referencedCatalog() != null) {
            where.append(" in catalog [").append(key.referencedCatalog()).append(']');
        }
        return where.toString();
    }

    /**
     * Computes the order in which tables will be filled, with referenced (parent) tables
     * appearing before the tables that depend on them.
     *
     * @param database the database to order
     * @return the tables in dependency-respecting fill order
     * @throws IllegalArgumentException if a foreign key's parent is not among {@code database}'s
     *                                  tables, or if the tables are mutually dependent; see
     *                                  {@link #requireForeignKeyTargetsPresent} and {@link #requireAcyclic}
     */
    static List<Table> fillOrder(Database database) {
        Graph<Table, DefaultEdge> graph = buildReversedDependencyGraph(database);
        requireAcyclic(graph);

        List<Table> ordered = new ArrayList<>();
        new TopologicalOrderIterator<>(graph).forEachRemaining(ordered::add);
        return ordered;
    }

    /**
     * Generates a DOT notation visualization of the table dependency graph.
     *
     * <p>Creates a Graphviz-compatible DOT representation of the table relationships
     * and provides a URL to view the graph online. The graph shows the order in
     * which tables will be filled to satisfy foreign key constraints.
     *
     * @param graph the table dependency graph to visualize
     * @param databaseName the name of the database for graph labeling
     */
    private void visualizeGraph(Graph<Table, DefaultEdge> graph, String databaseName) {
        // DEBUG-only: the DOT and the GraphvizOnline link embed the full schema topology (every
        // table name), which default-level logs shipped to aggregation shouldn't carry — and both
        // are schema-scaled allocations worth skipping when disabled
        if (!logger.isDebugEnabled()) {
            return;
        }

        DOTExporter<Table, DefaultEdge> exporter = new DOTExporter<>(Table::name);
        exporter.setVertexAttributeProvider((v) -> {
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v.name()));
            return map;
        });

        exporter.setGraphIdProvider(() -> databaseName);

        Writer writer = new StringWriter();
        exporter.exportGraph(graph, writer);

        String graphAsString = writer.toString();

        logger.debug("database graph in DOT notation:\n\n{}", graphAsString);

        // for a very large schema the URL-encoded DOT is too long to be a usable link; skip the
        // encode (which can roughly triple the length) — the DOT itself was logged above
        if (graphAsString.length() > MAX_GRAPH_LINK_CHARS) {
            logger.debug("database graph has {} chars of DOT notation — too large for a GraphvizOnline link", graphAsString.length());
            return;
        }

        String encodedDiagram = URLEncoder.encode(graphAsString, StandardCharsets.UTF_8).replace("+", "%20");

        logger.debug("Use this link to visualize the database graph:  https://dreampuf.github.io/GraphvizOnline/#{}", encodedDiagram);
    }

    /**
     * Builder for constructing DatabaseFiller instances.
     *
     * <p>Follows the builder pattern to provide a clean API for creating
     * DatabaseFiller objects with required dependencies.
     */
    public static class Builder {

        private final Connection connection;
        private final DataSource dataSource;
        private final DatabaseConfiguration configuration;

        private int threads = 1;
        private final List<SqlScript> beforeHooks = new ArrayList<>();
        private final List<SqlScript> afterHooks = new ArrayList<>();
        private final List<String> includePatterns = new ArrayList<>();
        private final List<String> excludePatterns = new ArrayList<>();
        private String catalog;
        private String schema;
        private Instant asOf;
        private Clock clock = Clock.systemUTC();

        /**
         * Creates a builder that fills sequentially on a single caller-managed connection — the
         * default, back-compatible mode. {@link #threads(int)} has no effect in this mode (a single
         * connection cannot be shared across threads); use {@link #Builder(DataSource, DatabaseConfiguration)}
         * for parallel fills.
         *
         * @param connection the database connection to use for filling operations
         * @param configuration the configuration specifying batch sizes, record counts, and table settings
         */
        public Builder(Connection connection, DatabaseConfiguration configuration) {
            this.connection = Objects.requireNonNull(connection, "connection must not be null");
            this.dataSource = null;
            this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        }

        /**
         * Creates a builder that fills from a pooled {@link DataSource}. With the default of one
         * thread the fill is sequential (on a single borrowed connection); call {@link #threads(int)}
         * with a value greater than one to fill independent tables concurrently, one connection per
         * worker.
         *
         * @param dataSource the data source to borrow worker connections from
         * @param configuration the configuration specifying batch sizes, record counts, and table settings
         */
        public Builder(DataSource dataSource, DatabaseConfiguration configuration) {
            this.connection = null;
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
            this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        }

        /**
         * Sets the number of worker threads used to fill independent tables concurrently. Only
         * effective when the builder was created with a {@link DataSource}; the default of {@code 1}
         * keeps the fill sequential.
         *
         * @param threads the worker-thread count; must be at least {@code 1}
         * @return this builder
         * @throws IllegalArgumentException if {@code threads} is less than {@code 1}
         */
        public Builder threads(int threads) {
            if (threads < 1) {
                throw new IllegalArgumentException("threads must be >= 1");
            }
            this.threads = threads;
            return this;
        }

        /**
         * Adds a SQL script to run before the fill: ahead of the schema read and before any table is
         * written, on the fill's connection (the supplied {@link Connection}, or one borrowed from the
         * {@link DataSource}). Before hooks run in the order added, and a failing one fails
         * {@link DatabaseFiller#fill()} with nothing filled. Typical uses are creating the schema or
         * truncating tables so a re-run does not collide with the previous run's rows (a fill is
         * not idempotent).
         *
         * <p>See {@link SqlScriptRunner} for the syntax, token and transaction rules.
         *
         * @param script the script to run
         * @return this builder
         * @since 3.3.0
         */
        public Builder before(SqlScript script) {
            beforeHooks.add(Objects.requireNonNull(script, "script must not be null"));
            return this;
        }

        /**
         * Adds a SQL script to run after every table has been filled, on the fill's connection (the
         * supplied {@link Connection}, or one borrowed from the {@link DataSource} once the parallel
         * fill has finished). After hooks run in the order added; a failure is thrown from
         * {@link DatabaseFiller#fill()} and is not swallowed. They do not run if the fill itself
         * fails. Typical use is computing derived tables from the generated rows, so they cannot
         * disagree with their sources.
         *
         * <p>The tables to fill are read before the after hooks run, so a table an after hook creates
         * is not filled; a derived table that already exists is filled like any other, so the hook
         * should empty it first. See {@link SqlScriptRunner} for the syntax, token and transaction
         * rules.
         *
         * @param script the script to run
         * @return this builder
         * @since 3.3.0
         */
        public Builder after(SqlScript script) {
            afterHooks.add(Objects.requireNonNull(script, "script must not be null"));
            return this;
        }

        /**
         * Fills the tables of this schema instead of the connection's current one. The schema is set
         * on every connection the fill uses (metadata discovery, each worker, and the hook phases, so
         * an unqualified table name in a hook script resolves in this schema) and the previous schema
         * is restored afterwards: on a supplied {@link Connection} when {@link DatabaseFiller#fill()}
         * returns or throws, on a {@link DataSource} connection before it goes back to the pool.
         *
         * <p>The name is passed to {@link Connection#setSchema(String)} as given, so its case must
         * match the database's. If the driver does not support selecting a schema (MySQL and MariaDB
         * treat a database as a catalog, so use {@link #catalog(String)}), or the schema does not
         * exist, {@code fill()} fails with a {@link SQLException} before doing anything else. Table
         * patterns given to {@link #includeTables(String...)} and {@link #excludeTables(String...)}
         * match table names within this schema.
         *
         * <p>Where a driver implements {@code setSchema} by replacing the session's whole search path
         * (PostgreSQL does), restoring puts back the single schema the connection reported before,
         * not a multi-entry search path.
         *
         * @param schema the schema to fill
         * @return this builder
         * @throws NullPointerException     if {@code schema} is null
         * @throws IllegalArgumentException if {@code schema} is blank
         * @since 3.3.0
         */
        public Builder schema(String schema) {
            this.schema = requireName(schema, "schema");
            return this;
        }

        /**
         * Fills the tables of this catalog instead of the connection's current one; on MySQL and
         * MariaDB the catalog is the database. Applied and restored exactly as described for
         * {@link #schema(String)}, and it fails the same way where the driver cannot switch catalog
         * (PostgreSQL cannot change database on a connection).
         *
         * @param catalog the catalog to fill
         * @return this builder
         * @throws NullPointerException     if {@code catalog} is null
         * @throws IllegalArgumentException if {@code catalog} is blank
         * @since 3.3.0
         */
        public Builder catalog(String catalog) {
            this.catalog = requireName(catalog, "catalog");
            return this;
        }

        /**
         * Restricts the fill to tables whose names match any of these patterns; with no include
         * pattern every table of the schema is filled. Additive: each call adds patterns.
         *
         * <p>A pattern is an unqualified table name matched case-insensitively (as
         * {@link TableConfiguration} names are), in which {@code *} matches any run of characters and
         * {@code ?} exactly one; nothing else is special. It is matched against the tables of the
         * {@linkplain #schema(String) selected schema}. A pattern that matches no table fails
         * {@link DatabaseFiller#fill()} with an {@link IllegalArgumentException} naming it, since an
         * empty selection is nearly always a typo. Exclusions ({@link #excludeTables(String...)}) are
         * applied to what the includes kept.
         *
         * <p>A selected table with a foreign key to a table that is not selected fails the fill before
         * any row is written; include the parent too.
         *
         * @param patterns table name patterns
         * @return this builder
         * @throws NullPointerException     if {@code patterns} or any pattern is null
         * @throws IllegalArgumentException if any pattern is blank
         * @since 3.3.0
         */
        public Builder includeTables(String... patterns) {
            Objects.requireNonNull(patterns, "patterns must not be null");
            return includeTables(List.of(patterns));
        }

        /**
         * Collection form of {@link #includeTables(String...)}.
         *
         * @param patterns table name patterns
         * @return this builder
         * @throws NullPointerException     if {@code patterns} or any pattern is null
         * @throws IllegalArgumentException if any pattern is blank
         * @since 3.3.0
         */
        public Builder includeTables(Collection<String> patterns) {
            Objects.requireNonNull(patterns, "patterns must not be null");
            patterns.forEach(pattern -> includePatterns.add(TableSelection.requirePattern(pattern)));
            return this;
        }

        /**
         * Leaves tables whose names match any of these patterns unfilled: the way to keep a derived
         * table (a rollup an {@link #after(SqlScript) after} hook computes) free of random rows.
         * Additive; the pattern syntax is that of {@link #includeTables(String...)}, and exclusions
         * apply after inclusions.
         *
         * <p>A pattern that matches no table only logs a warning. Excluding a table that no other table
         * references just works; excluding a table that a selected table references fails
         * {@link DatabaseFiller#fill()} before any row is written, naming both tables.
         *
         * @param patterns table name patterns
         * @return this builder
         * @throws NullPointerException     if {@code patterns} or any pattern is null
         * @throws IllegalArgumentException if any pattern is blank
         * @since 3.3.0
         */
        public Builder excludeTables(String... patterns) {
            Objects.requireNonNull(patterns, "patterns must not be null");
            return excludeTables(List.of(patterns));
        }

        /**
         * Collection form of {@link #excludeTables(String...)}.
         *
         * @param patterns table name patterns
         * @return this builder
         * @throws NullPointerException     if {@code patterns} or any pattern is null
         * @throws IllegalArgumentException if any pattern is blank
         * @since 3.3.0
         */
        public Builder excludeTables(Collection<String> patterns) {
            Objects.requireNonNull(patterns, "patterns must not be null");
            patterns.forEach(pattern -> excludePatterns.add(TableSelection.requirePattern(pattern)));
            return this;
        }

        /**
         * Pins the anchor that {@link io.bloviate.gen.RelativeWindow relative date windows} ("within the
         * last 90 days") are measured from. One anchor is used for the whole fill, by every table,
         * partition and worker thread.
         *
         * <p><strong>Reproducibility.</strong> The same seed and the same pinned {@code asOf} produce
         * identical output on every run and JDK. Without a pinned {@code asOf} the anchor is the start of
         * the current UTC day (00:00Z), read from the clock when {@link DatabaseFiller#fill()} begins and
         * logged once at INFO when a relative window first uses it; the data is then reproducible only by
         * pinning the logged instant (also available from {@link DatabaseFiller#asOf()}). A fill that uses
         * no relative window is unaffected either way and never reads the clock for its data.
         *
         * @param asOf the anchor instant, used as is; null (the default) resolves it from the clock
         * @return this builder
         * @since 3.7.0
         */
        public Builder asOf(Instant asOf) {
            this.asOf = asOf;
            return this;
        }

        /**
         * Reads the clock an unpinned {@code asOf} is resolved from. Package-private so a test can settle
         * the anchor without depending on the day it happens to run.
         */
        Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        private static String requireName(String value, String what) {
            Objects.requireNonNull(value, what + " must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(what + " must not be blank");
            }
            return value;
        }

        /**
         * Builds a new DatabaseFiller instance with the configured parameters.
         *
         * @return a new DatabaseFiller ready to fill the database
         */
        public DatabaseFiller build() {
            return new DatabaseFiller(this);
        }
    }

    /**
     * Private constructor used by the Builder to create DatabaseFiller instances.
     *
     * @param builder the builder containing the configured parameters
     */
    private DatabaseFiller(Builder builder) {
        this.connection = builder.connection;
        this.dataSource = builder.dataSource;
        this.configuration = builder.configuration;
        this.threads = builder.threads;
        this.beforeHooks = List.copyOf(builder.beforeHooks);
        this.afterHooks = List.copyOf(builder.afterHooks);
        this.tableSelection = new TableSelection(builder.includePatterns, builder.excludePatterns);
        this.schemaSelection = new SchemaSelection(builder.catalog, builder.schema);
        this.asOf = builder.asOf;
        this.clock = builder.clock;
        // a pinned anchor is known now; an unpinned one is settled when fill() starts
        this.generationContext = asOf != null ? GenerationContext.pinned(asOf) : null;

        if (connection != null && threads > 1) {
            logger.warn("threads({}) is ignored when filling on a single Connection; use the DataSource constructor for parallel fills", threads);
        }
    }
}
