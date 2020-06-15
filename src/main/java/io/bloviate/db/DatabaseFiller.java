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

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final int rows;
    private final int batchSize;

    @Override
    public void fill() throws SQLException {

        Database database = DatabaseUtils.getMetadata(connection);

        Graph<Table, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (Table table : database.getTables()) {

            List<ForeignKey> foreignKeys = table.getForeignKeys();

            g.addVertex(table);

            if (foreignKeys != null && !foreignKeys.isEmpty()) {
                for (ForeignKey key : foreignKeys) {
                    Table referencedTable = database.getTable(key.getForeignTable());
                    if (!g.containsVertex(referencedTable)) {
                        g.addVertex(referencedTable);
                    }
                    g.addEdge(table, referencedTable);
                }
            }

        }

        List<Table> ordered = new ArrayList<>();

        Iterator<Table> toi = new TopologicalOrderIterator<>(g);
        while (toi.hasNext()) {
            ordered.add(toi.next());
        }

        Collections.reverse(ordered);

        for (Table table : ordered) {
            logger.debug(table.getName());
        }

        /*new TableFiller.Builder(connection, tableName)
                .catalog(catalog)
                .schemaPattern(schema)
                .batchSize(batchSize)
                .rows(rows)
                .build().fill();*/

    }

    public static class Builder {

        private final Connection connection;

        private int rows = 1000;
        private int batchSize = 128;

        public Builder(Connection connection) {
            this.connection = connection;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public DatabaseFiller build() {
            return new DatabaseFiller(this);
        }
    }

    private DatabaseFiller(Builder builder) {
        this.connection = builder.connection;
        this.rows = builder.rows;
        this.batchSize = builder.batchSize;
    }
}
