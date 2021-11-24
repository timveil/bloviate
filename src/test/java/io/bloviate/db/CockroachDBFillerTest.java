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

import io.bloviate.ext.CockroachDBSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

class CockroachDBFillerTest extends BaseDatabaseTestCase {

    @Test
    void fillDatabase() throws SQLException {

        try (CockroachContainer database = new CockroachContainer("cockroachdb/cockroach:latest")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withInitScript("create_tpcc.cockroachdb.sql")
                .withCommand("start-single-node --insecure --store=type=mem,size=.75")) {

            database.start();

            DataSource dataSource = getDataSource(database);

            Set<TableConfiguration> tableConfigurations = new HashSet<>();
            DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new CockroachDBSupport(), tableConfigurations);

            try (Connection connection = dataSource.getConnection()) {
                new DatabaseFiller.Builder(connection, configuration).build().fill();
            }

        }
    }

    @Test
    void fillDatabaseWithConfigs() throws SQLException {

        try (CockroachContainer database = new CockroachContainer("cockroachdb/cockroach:latest")
                .withUrlParam("rewriteBatchedInserts", "true")
                .withInitScript("create_tpcc.cockroachdb.sql")
                .withCommand("start-single-node --insecure --store=type=mem,size=.75")) {

            database.start();

            DataSource dataSource = getDataSource(database);

            int numWarehouses = 1;
            int numItems = 100000;
            int stock = 100000;
            int districtsPerWarehouse = 10;
            int districts = numWarehouses * districtsPerWarehouse;
            int customersPerDistrict = 3000;
            int customers = districts * customersPerDistrict;
            int history = customers;
            int openOrder = customers;
            int newOrder = customers;
            int orderLine = customers * 10;

            Set<TableConfiguration> tableConfigurations = new HashSet<>();
            tableConfigurations.add(new TableConfiguration("warehouse", numWarehouses));
            tableConfigurations.add(new TableConfiguration("item", numItems));
            tableConfigurations.add(new TableConfiguration("stock", stock));
            tableConfigurations.add(new TableConfiguration("district", districts));
            tableConfigurations.add(new TableConfiguration("customer", customers));
            tableConfigurations.add(new TableConfiguration("history", history));
            tableConfigurations.add(new TableConfiguration("open_order", openOrder));
            tableConfigurations.add(new TableConfiguration("new_order", newOrder));
            tableConfigurations.add(new TableConfiguration("order_line", orderLine));

            DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new CockroachDBSupport(), tableConfigurations);

            try (Connection connection = dataSource.getConnection()) {
                new DatabaseFiller.Builder(connection, configuration).build().fill();
            }
        }
    }
}