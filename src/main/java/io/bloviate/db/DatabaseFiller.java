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

import io.bloviate.util.DatabaseUtils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

public class DatabaseFiller implements Fillable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final DatabaseConfiguration configuration;
    private final Map<String, TableConfiguration> tableConfigurationMap;

    @Override
    public void fill() throws SQLException {

        Database database = DatabaseUtils.getMetadata(connection);

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
                    graph.addEdge(table, referencedTable);
                }
            }
        }

        List<Table> ordered = new ArrayList<>();

        Iterator<Table> toi = new TopologicalOrderIterator<>(graph);
        while (toi.hasNext()) {
            ordered.add(toi.next());
        }

        Collections.reverse(ordered);

        for (Table table : ordered) {
            logger.debug("filling table [{}]", table.name());
            TableConfiguration tableConfiguration = tableConfigurationMap.get(table.name());

            long rowCount = configuration.defaultRowCount();

            if (tableConfiguration != null) {
                rowCount = tableConfiguration.rowCount();
            }

            new TableFiller.Builder(connection, database, configuration)
                    .table(table)
                    .rows(rowCount)
                    .build().fill();
        }

    }

    public static class Builder {

        private final Connection connection;
        private final DatabaseConfiguration configuration;
        private Map<String, TableConfiguration> tableConfigurationMap = new HashMap<>();

        public Builder(Connection connection, DatabaseConfiguration configuration) {
            this.connection = connection;
            this.configuration = configuration;
        }

        public Builder tables(Map<String, TableConfiguration> tableConfigurations) {
            this.tableConfigurationMap = tableConfigurations;
            return this;
        }

        public Builder addTable(TableConfiguration tableConfiguration) {
            this.tableConfigurationMap.put(tableConfiguration.tableName(), tableConfiguration);
            return this;
        }

        public DatabaseFiller build() {
            return new DatabaseFiller(this);
        }
    }

    private DatabaseFiller(Builder builder) {
        this.connection = builder.connection;
        this.configuration = builder.configuration;
        this.tableConfigurationMap = builder.tableConfigurationMap;
    }
}
