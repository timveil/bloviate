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

import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.tpcc.TPCCConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class PostgresFillerTest extends BasePostgresTest {

    @Test
    void fillTPCC() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), null);
        fillDatabase("create_tpcc.postgres.sql", configuration, null);
    }

    @Test
    void fillTPCCWithConfigs() throws SQLException {

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 10, new PostgresSupport(), TPCCConfiguration.build(1));
        fillDatabase("create_tpcc.postgres.sql", configuration, new Validator() {
            @Override
            public void validate(Connection connection, DatabaseConfiguration configuration) throws SQLException {
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select count(*) from item")) {
                    rs.next();
                    Assertions.assertEquals(rs.getInt(1), configuration.tableConfiguration("item").orElseThrow().rowCount());
                }

                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select max(i_id) from item")) {
                    rs.next();
                    int maxId = rs.getInt(1);
                    Assertions.assertTrue(maxId <= 100000, String.format("max i_id is %d", maxId));
                }
            }
        });

    }
}