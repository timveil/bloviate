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

import io.bloviate.ext.MariaDBSupport;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

class MariaDbFillerTest extends BaseMariaDbTest {

    @Test
    void fillTestTables() throws SQLException {
        // standard_table exercises the full set of standard MariaDB types with zero configuration.
        // json_doc.a is a MariaDB JSON column, which the driver reports as LONGTEXT and which carries
        // an automatic json_valid CHECK -- so it must be filled with a per-column JsonbGenerator
        // override (a plain string generator would violate the constraint).
        Set<ColumnConfiguration> jsonColumns = new HashSet<>();
        jsonColumns.add(new ColumnConfiguration("a", random -> new JsonbGenerator.Builder(random).build()));

        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        tableConfigurations.add(new TableConfiguration("json_doc", 5, jsonColumns));

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 5, new MariaDBSupport(), tableConfigurations);
        fillDatabase("create_tables.mariadb.sql", configuration, connection -> {
            assertRowCount(connection, "json_doc", 5);
            assertRowCount(connection, "standard_table", 5);
        });
    }

    @Test
    void fillTPCC() throws SQLException {
        // the MySQL TPC-C DDL is MariaDB-compatible (AUTO_INCREMENT, standard types)
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new MariaDBSupport(), tableConfigurations);
        fillDatabase("create_tpcc.mysql.sql", configuration);
    }

    @Test
    void fillTPCCWithColumnConfigs() throws SQLException {

        int w = 2;
        int i = 10;
        int d = 2;
        int c = 3;
        int minLines = 5;
        int maxLines = 15;
        int newOrders = 2;

        DatabaseConfiguration configuration = new DatabaseConfiguration(
                128, 10, new MariaDBSupport(), TPCCConfiguration.build(w, i, d, c, minLines, maxLines, newOrders));

        fillDatabase("create_tpcc.mysql.sql", configuration, connection -> {
            assertRowCount(connection, "warehouse", w);
            assertRowCount(connection, "item", i);
            assertRowCount(connection, "stock", (long) w * i);
            assertRowCount(connection, "district", (long) w * d);
            assertRowCount(connection, "customer", (long) w * d * c);
            assertRowCount(connection, "history", (long) w * d * c);
            assertRowCount(connection, "open_order", (long) w * d * c);
            assertRowCount(connection, "new_order", (long) w * d * newOrders);

            assertTpccColumnFidelity(connection, c, minLines, maxLines, newOrders);
        });
    }
}
