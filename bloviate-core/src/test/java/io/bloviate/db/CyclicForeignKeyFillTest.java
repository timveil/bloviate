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

import io.bloviate.ext.H2Support;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #618: two tables that reference each other cannot be filled in any order, and the fill paths
 * used to disagree about what to do with them. The sequential path let a bare "Graph is not a DAG" out
 * of the topological iterator; the level-parallel path logged a warning and <strong>filled neither
 * table</strong>, so a fill returned normally having silently left them empty. Both now fail the same
 * way, before a row is written, naming the cycle.
 *
 * <p>Against in-memory H2, so this runs without Docker on the paths a real fill takes — the unit-level
 * ordering cases are in {@link DatabaseFillerOrderTest}.
 */
class CyclicForeignKeyFillTest {

    private static final int ROWS = 5;

    /**
     * A mutual pair, plus a table that has nothing to do with them. The foreign keys are added after
     * both tables exist, since neither can name the other at CREATE time.
     */
    private static final String SCHEMA = """
            create table invoice (id int primary key, payment_id int);
            create table payment (id int primary key, invoice_id int);
            alter table invoice add constraint fk_invoice_payment foreign key (payment_id) references payment (id);
            alter table payment add constraint fk_payment_invoice foreign key (invoice_id) references invoice (id);
            create table unrelated (id int primary key, note varchar(20));
            """;

    private String url;

    @BeforeEach
    void freshDatabase() throws SQLException {
        url = "jdbc:h2:mem:cyclic_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", SCHEMA));
        }
    }

    private static DatabaseConfiguration configuration() {
        return new DatabaseConfiguration(16, ROWS, new H2Support(), null, 42L);
    }

    @Test
    void theSequentialPathFailsNamingTheCycle() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DatabaseFiller.Builder(connection, configuration()).build().fill());

            assertCycleReported(e);
            assertNothingWritten(connection);
        }
    }

    /** The path that used to return normally with the cyclic tables silently left empty. */
    @Test
    void theLevelParallelPathFailsTheSameWayInsteadOfSkippingTheTables() throws SQLException {
        try (TrackingDataSource dataSource = new TrackingDataSource(url, null, null, 3);
             Connection connection = DriverManager.getConnection(url)) {

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DatabaseFiller.Builder(dataSource, configuration()).threads(3).build().fill());

            assertCycleReported(e);
            assertNothingWritten(connection);
        }
    }

    /**
     * Excluding the whole cycle leaves an orderable schema, which is one of the ways out the error
     * message offers. It has to be the whole cycle: dropping one side leaves the other referencing a
     * table that is not being filled, which {@code requireForeignKeyTargetsPresent} rejects — so the
     * message says every table of it, and this pins both halves of that.
     */
    @Test
    void excludingTheWholeCycleFillsAndExcludingOneSideDoesNot() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url)) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DatabaseFiller.Builder(connection, configuration()).excludeTables("invoice").build().fill());
            assertTrue(e.getMessage().contains("not among the tables being filled"), e.getMessage());

            new DatabaseFiller.Builder(connection, configuration()).excludeTables("invoice", "payment").build().fill();

            assertEquals(ROWS, count(connection, "unrelated"));
            assertEquals(0, count(connection, "invoice"));
            assertEquals(0, count(connection, "payment"));
        }
    }

    private static void assertCycleReported(IllegalArgumentException e) {
        assertTrue(e.getMessage().contains("INVOICE") && e.getMessage().contains("PAYMENT"),
                "the error must name the tables in the cycle: " + e.getMessage());
        assertTrue(e.getMessage().contains("Nothing was written"), e.getMessage());
    }

    private static void assertNothingWritten(Connection connection) throws SQLException {
        assertEquals(0, count(connection, "invoice"));
        assertEquals(0, count(connection, "payment"));
        assertEquals(0, count(connection, "unrelated"), "the fill must fail before writing anything at all");
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
