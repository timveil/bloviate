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

import io.bloviate.util.DatabaseUtils;
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

import java.io.StringWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final DatabaseConfiguration configuration;

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
        Database database = DatabaseUtils.getMetadata(connection);
        metadataWatch.stop();

        logger.info(metadataWatch.toString());

        StopWatch databaseWatch = new StopWatch(String.format("filled database [%s] in", database.catalog()));
        databaseWatch.start();

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
                        logger.warn("this key is self referencing... will likely cause problems");
                    }

                    try {
                        graph.addEdge(table, referencedTable);
                    } catch (IllegalArgumentException e) {
                        logger.error(String.format("error adding edge between %s and %s: %s", table.name(), referencedTable.name(), e.getMessage()), e);
                    }
                }
            }
        }


        EdgeReversedGraph<Table, DefaultEdge> reversedGraph = new EdgeReversedGraph<>(graph);

        visualizeGraph(reversedGraph, database.catalog());

        TopologicalOrderIterator<Table, DefaultEdge> iterator = new TopologicalOrderIterator<>(reversedGraph);

        while (iterator.hasNext()) {
            new TableFiller.Builder(connection, database, configuration)
                    .table(iterator.next())
                    .build().fill();
        }

        databaseWatch.stop();

        logger.info(databaseWatch.toString());

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
        private final DatabaseConfiguration configuration;

        /**
         * Creates a new builder with the required dependencies.
         * 
         * @param connection the database connection to use for filling operations
         * @param configuration the configuration specifying batch sizes, record counts, and table settings
         * @throws NullPointerException if either parameter is null
         */
        public Builder(Connection connection, DatabaseConfiguration configuration) {
            this.connection = connection;
            this.configuration = configuration;
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
        this.configuration = builder.configuration;
    }
}
