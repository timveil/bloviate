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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    /** A caller-managed connection for the sequential path; null when filling from a {@link DataSource}. */
    private final Connection connection;

    /** A pooled data source for the parallel / self-managed path; null when a {@link Connection} was supplied. */
    private final DataSource dataSource;

    private final DatabaseConfiguration configuration;

    /** Worker threads for parallel table fill; {@code 1} (the default) keeps the fill sequential. */
    private final int threads;

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
     * @throws SQLException if any database operation fails during the filling process
     */
    @Override
    public void fill() throws SQLException {

        StopWatch metadataWatch = new StopWatch("fetched database metadata in");
        metadataWatch.start();
        // a Connection was supplied for the sequential path; otherwise borrow one from the pool
        Database database = connection != null
                ? DatabaseUtils.getMetadata(connection)
                : DatabaseUtils.getMetadata(dataSource);
        metadataWatch.stop();

        logger.debug("{}", metadataWatch);

        StopWatch databaseWatch = new StopWatch(String.format("filled database [%s] in", database.catalog()));
        databaseWatch.start();

        Graph<Table, DefaultEdge> reversedGraph = buildReversedDependencyGraph(database);

        visualizeGraph(reversedGraph, database.catalog());

        // recommend the driver batch-rewrite URL parameter once per fill if it is missing
        if (connection != null) {
            warnIfBatchRewriteMissing(connection);
        } else {
            try (Connection conn = dataSource.getConnection()) {
                warnIfBatchRewriteMissing(conn);
            }
        }

        if (connection != null) {
            // back-compat path: fill sequentially on the caller's single connection (unchanged)
            warnIfPartitionsIgnored();
            warnIfBulkIgnored();
            fillSequential(connection, database, reversedGraph);
        } else if (threads > 1) {
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
        } else {
            // DataSource supplied but no parallelism requested: borrow one connection, fill in order
            warnIfPartitionsIgnored();
            warnIfBulkIgnored();
            try (Connection conn = dataSource.getConnection()) {
                fillSequential(conn, database, reversedGraph);
            }
        }

        databaseWatch.stop();

        logger.info("{}", databaseWatch);

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
        TopologicalOrderIterator<Table, DefaultEdge> iterator = new TopologicalOrderIterator<>(reversedGraph);
        while (iterator.hasNext()) {
            new TableFiller.Builder(conn, database, configuration)
                    .table(iterator.next())
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

                // barrier: every table in this level must finish before the next level may start
                for (Future<Void> future : executor.invokeAll(tasks)) {
                    awaitFuture(future);
                }
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
    private void fillUnordered(Database database, Graph<Table, DefaultEdge> graph) throws SQLException {
        DatabaseSupport support = configuration.databaseSupport();

        // probe once: if we can't disable+re-enable constraints on a borrowed connection, fall back to
        // the ordered path instead of fanning out into a partially-disabled state. As in the worker
        // bodies, a failed re-enable aborts the connection rather than returning it to the pool still
        // in its constraint-disabled state (see restoreConstraints).
        try (Connection conn = dataSource.getConnection()) {
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
            for (Future<Void> future : executor.invokeAll(tasks)) {
                awaitFuture(future);
            }
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

    /** Unwraps a worker future, re-throwing the underlying {@link SQLException} or runtime failure. */
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
                        .commitStrategy(effectiveParallelCommitStrategy())
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
                        .commitStrategy(effectiveParallelCommitStrategy())
                        .rowRange(startInclusive, endExclusive)
                        .build().fill());
    }

    /**
     * Borrows a connection from the pool and runs {@code body} on it. When {@code bulk} is true,
     * foreign-key enforcement is disabled on the connection's session before {@code body} and restored
     * in a {@code finally} <em>before</em> the connection returns to the pool — so a constraint-disabled
     * connection never leaks to other pool users, even if the fill throws. If the restore itself fails,
     * the connection is aborted rather than returned to the pool (see {@link #restoreConstraints}). The
     * disable/enable mechanism is database-specific (see {@link DatabaseSupport#disableConstraints}).
     */
    private void fillOnPooledConnection(Database database, boolean bulk, ConnectionFill body) throws SQLException {
        DatabaseSupport support = configuration.databaseSupport();
        try (Connection conn = dataSource.getConnection()) {
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
     * The commit strategy used by parallel workers. A pooled worker connection must not be left on
     * the connection's autocommit (that would commit per batch and lose the per-table transaction),
     * so {@link CommitStrategy.Mode#CONNECTION_DEFAULT} maps to {@link CommitStrategy#perTable()};
     * any explicitly configured strategy (e.g. {@link CommitStrategy#everyNBatches(int)}) is honored.
     */
    private CommitStrategy effectiveParallelCommitStrategy() {
        CommitStrategy configured = configuration.commitStrategy();
        return configured.mode() == CommitStrategy.Mode.CONNECTION_DEFAULT
                ? CommitStrategy.perTable()
                : configured;
    }

    /**
     * Partitions the dependency graph into topological levels via Kahn's algorithm: level 0 holds
     * every table that references nothing, level 1 the tables whose parents are all in level 0, and
     * so on. Tables in the same level are mutually independent and therefore safe to fill in
     * parallel. Iteration order of the graph's vertex set is preserved, so levels are deterministic.
     *
     * <p>If the graph contains a cycle (e.g. mutually referencing tables), the tables in the cycle
     * never reach in-degree zero and are omitted from the levels — matching the sequential
     * {@link TopologicalOrderIterator}, which likewise cannot order a cycle.
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
     */
    Graph<Table, DefaultEdge> buildReversedDependencyGraph(Database database) {
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
     * Computes the order in which tables will be filled, with referenced (parent) tables
     * appearing before the tables that depend on them.
     *
     * @param database the database to order
     * @return the tables in dependency-respecting fill order
     */
    List<Table> fillOrder(Database database) {
        List<Table> ordered = new ArrayList<>();
        new TopologicalOrderIterator<>(buildReversedDependencyGraph(database)).forEachRemaining(ordered::add);
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

        String encodedDiagram = URLEncoder.encode(graphAsString, StandardCharsets.UTF_8).replace("+", "%20");

        logger.info("Use this link to visualize the database graph:  https://dreampuf.github.io/GraphvizOnline/#{}", encodedDiagram);
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
            this.connection = connection;
            this.dataSource = null;
            this.configuration = configuration;
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
            this.dataSource = dataSource;
            this.configuration = configuration;
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

        if (connection != null && threads > 1) {
            logger.warn("threads({}) is ignored when filling on a single Connection; use the DataSource constructor for parallel fills", threads);
        }
    }
}
