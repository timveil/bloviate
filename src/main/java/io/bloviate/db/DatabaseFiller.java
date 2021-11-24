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
import org.apache.commons.lang3.time.StopWatch;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class DatabaseFiller implements Fillable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final DatabaseConfiguration configuration;

    @Override
    public void fill() throws SQLException {

        Database database = DatabaseUtils.getMetadata(connection);

        StopWatch databaseWatch = new StopWatch(String.format("filled database [%s] in", database.catalog()));
        databaseWatch.start();

        logger.debug(database.toString());

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
            StopWatch tableWatch = new StopWatch(String.format("filled table [%s] in", table.name()));
            tableWatch.start();

            new TableFiller.Builder(connection, database, configuration)
                    .table(table)
                    .build().fill();

            tableWatch.stop();

            logger.info(tableWatch.toString());
        }

        databaseWatch.stop();

        logger.info(databaseWatch.toString());

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
