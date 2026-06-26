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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseDatabaseTestCase {

    protected static final Logger logger = LoggerFactory.getLogger(BaseDatabaseTestCase.class);

    protected static DataSource getDataSource(JdbcDatabaseContainer<?> container) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(container.getJdbcUrl());
        hikariConfig.setUsername(container.getUsername());
        hikariConfig.setPassword(container.getPassword());
        hikariConfig.setDriverClassName(container.getDriverClassName());
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Callback invoked against the live connection after a database has been filled,
     * allowing a test to assert on the generated data before the container is torn down.
     */
    @FunctionalInterface
    protected interface Verifier {
        void verify(Connection connection) throws SQLException;
    }

    protected static void assertRowCount(Connection connection, String table, long expected) throws SQLException {
        assertCount(connection, "select count(*) from " + table, expected);
    }

    protected static void assertCount(Connection connection, String query, long expected) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            assertEquals(expected, resultSet.getLong(1), query);
        }
    }

    /**
     * Asserts that a TPC-C dataset produced by {@code TPCCConfiguration} matches the spec's
     * value ranges and seed values (issue #421, gaps 1, 2 and 3). The SQL is portable across
     * PostgreSQL, MySQL and CockroachDB.
     *
     * @param connection      an open connection to the filled database
     * @param customers       customers (and orders) per district
     * @param minLines        minimum order lines per order (inclusive)
     * @param maxLines        maximum order lines per order (inclusive)
     * @param newOrders       most-recent orders per district mirrored into {@code new_order}
     */
    protected static void assertTpccColumnFidelity(Connection connection, int customers, int minLines, int maxLines, int newOrders) throws SQLException {
        // realistic value generators were applied (unchanged from the original TPC-C support)
        assertCount(connection, "select count(*) from customer where c_credit not in ('GC','BC')", 0);
        assertCount(connection, "select count(*) from customer where c_zip not like '____11111'", 0);
        assertCount(connection, "select count(*) from customer where c_middle <> 'OE'", 0);

        // gap 2: each order has o_ol_cnt lines in [minLines, maxLines], and order_line holds
        // exactly that many rows per order (o_ol_cnt agrees with the actual line count)
        assertCount(connection, "select count(*) from open_order where o_ol_cnt < " + minLines + " or o_ol_cnt > " + maxLines, 0);
        assertCount(connection, "select count(*) from open_order o where o.o_ol_cnt <> "
                + "(select count(*) from order_line l where l.ol_w_id = o.o_w_id and l.ol_d_id = o.o_d_id and l.ol_o_id = o.o_id)", 0);
        assertCount(connection, "select count(*) from order_line l join open_order o "
                + "on l.ol_w_id = o.o_w_id and l.ol_d_id = o.o_d_id and l.ol_o_id = o.o_id where l.ol_number > o.o_ol_cnt", 0);

        // gap 4: o_c_id is a per-district random permutation of customer ids (each customer
        // has exactly one order) — within range and with no duplicate within a district
        assertCount(connection, "select count(*) from open_order where o_c_id < 1 or o_c_id > " + customers, 0);
        assertCount(connection, "select count(*) from "
                + "(select o_w_id, o_d_id, o_c_id from open_order group by o_w_id, o_d_id, o_c_id having count(*) > 1) dup", 0);

        // gap 1: new_order holds only the most-recent orders per district (highest o_id values)
        assertCount(connection, "select count(*) from new_order where no_o_id < " + (customers - newOrders + 1), 0);

        // gap 3: spec value ranges for non-key columns
        assertCount(connection, "select count(*) from warehouse where w_tax < 0 or w_tax > 0.2", 0);
        assertCount(connection, "select count(*) from district where d_tax < 0 or d_tax > 0.2", 0);
        assertCount(connection, "select count(*) from customer where c_discount < 0 or c_discount > 0.5", 0);
        assertCount(connection, "select count(*) from item where i_price < 1 or i_price > 100", 0);
        assertCount(connection, "select count(*) from stock where s_quantity < 10 or s_quantity > 100", 0);
        assertCount(connection, "select count(*) from open_order where o_carrier_id < 1 or o_carrier_id > 10", 0);
        assertCount(connection, "select count(*) from order_line where ol_amount < 0 or ol_amount > 9999.99", 0);

        // gap 3: spec seed values
        assertCount(connection, "select count(*) from warehouse where w_ytd <> 300000.00", 0);
        assertCount(connection, "select count(*) from district where d_ytd <> 30000.00", 0);
        assertCount(connection, "select count(*) from district where d_next_o_id <> " + (customers + 1), 0);
        assertCount(connection, "select count(*) from stock where s_ytd <> 0 or s_order_cnt <> 0 or s_remote_cnt <> 0", 0);
        assertCount(connection, "select count(*) from customer where c_credit_lim <> 50000.00", 0);
        assertCount(connection, "select count(*) from customer where c_balance <> -10.00", 0);
        assertCount(connection, "select count(*) from customer where c_payment_cnt <> 1 or c_delivery_cnt <> 0", 0);
        assertCount(connection, "select count(*) from history where h_amount <> 10.00", 0);
        assertCount(connection, "select count(*) from order_line where ol_quantity <> 5", 0);
    }
}
