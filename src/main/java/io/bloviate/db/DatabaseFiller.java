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

public class DatabaseFiller implements Fillable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final DatabaseConfiguration configuration;

    @Override
    public void fill() throws SQLException {

        StopWatch metadataWatch = new StopWatch("fetched database metadata in");
        metadataWatch.start();
        Database database = DatabaseUtils.getMetadata(connection);
        metadataWatch.stop();

        logger.info(metadataWatch.toString());

        StopWatch databaseWatch = new StopWatch(String.format("filled database [%s] in", database));
        databaseWatch.start();

        try {

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
        } finally {
            databaseWatch.stop();
        }


        logger.info(databaseWatch.toString());

    }

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

    public static class Builder {

        private final Connection connection;
        private final DatabaseConfiguration configuration;

        public Builder(Connection connection, DatabaseConfiguration configuration) {
            this.connection = connection;
            this.configuration = configuration;
        }

        public DatabaseFiller build() {
            return new DatabaseFiller(this);
        }
    }

    private DatabaseFiller(Builder builder) {
        this.connection = builder.connection;
        this.configuration = builder.configuration;
    }
}
