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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SchemaSelection}: applying and restoring the catalog and schema on real H2 and
 * SQLite connections, and the failure paths (unsupported selection, failed restore) on a scripted stub
 * connection.
 */
class SchemaSelectionTest {

    /** A connection whose catalog/schema state and failures a test scripts; records what was called. */
    private static final class Stub {
        String catalog = "c0";
        String schema = "s0";
        /** When set, setCatalog reports success but the connection keeps its old catalog. */
        boolean ignoreSetCatalog;
        /** Values whose {@code setSchema} throws. */
        final List<String> failingSchemas = new ArrayList<>();
        /** Values whose {@code setCatalog} throws. */
        final List<String> failingCatalogs = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        boolean aborted;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        switch (name) {
                            case "getCatalog":
                                return catalog;
                            case "getSchema":
                                return schema;
                            case "setCatalog":
                                calls.add("setCatalog:" + args[0]);
                                if (failingCatalogs.contains((String) args[0])) {
                                    throw new SQLException("boom " + args[0]);
                                }
                                if (!ignoreSetCatalog) {
                                    catalog = (String) args[0];
                                }
                                return null;
                            case "setSchema":
                                calls.add("setSchema:" + args[0]);
                                if (failingSchemas.contains((String) args[0])) {
                                    throw new SQLException("boom " + args[0]);
                                }
                                schema = (String) args[0];
                                return null;
                            case "abort":
                                aborted = true;
                                return null;
                            default:
                                throw new UnsupportedOperationException(name);
                        }
                    });
        }
    }

    private static String h2Url() {
        return "jdbc:h2:mem:schemasel_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void nothingSelectedTouchesNothing() throws SQLException {
        Stub stub = new Stub();

        assertFalse(SchemaSelection.NONE.isSet());
        SchemaSelection.NONE.apply(stub.connection(), false).close();

        assertEquals(List.of(), stub.calls);
    }

    @Test
    void appliesAndRestoresTheSchemaOnH2() throws SQLException {
        try (Connection connection = DriverManager.getConnection(h2Url())) {
            connection.createStatement().execute("create schema other");

            try (SchemaSelection.Scope scope = new SchemaSelection(null, "OTHER").apply(connection, false)) {
                assertTrue(scope != null);
                assertEquals("OTHER", connection.getSchema());
            }

            assertEquals("PUBLIC", connection.getSchema());
        }
    }

    @Test
    void aSchemaThatDoesNotExistFailsWithoutLeavingAnythingChanged() throws SQLException {
        try (Connection connection = DriverManager.getConnection(h2Url())) {
            SchemaSelection selection = new SchemaSelection(null, "NOPE");

            assertThrows(SQLException.class, () -> selection.apply(connection, false));

            assertEquals("PUBLIC", connection.getSchema());
        }
    }

    @Test
    void aDriverThatCannotSelectASchemaIsAnErrorNotASilentNoOp() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SchemaSelection selection = new SchemaSelection(null, "other");

            SQLException e = assertThrows(SQLException.class, () -> selection.apply(connection, false));

            assertTrue(e.getMessage().contains("cannot select schema [other]"), e.getMessage());
            assertTrue(e.getMessage().contains("catalog"), "should point MySQL users at catalog(...): " + e.getMessage());
        }
    }

    @Test
    void aCatalogTheConnectionIgnoresIsAnError() {
        Stub stub = new Stub();
        stub.ignoreSetCatalog = true;

        SQLException e = assertThrows(SQLException.class, () -> new SchemaSelection("other_db", null).apply(stub.connection(), false));

        assertTrue(e.getMessage().contains("cannot select catalog [other_db]"), e.getMessage());
        assertTrue(e.getMessage().contains("[c0]"), "should say what the connection reports: " + e.getMessage());
    }

    @Test
    void selectingWhatTheConnectionAlreadyHasDoesNotTouchIt() throws SQLException {
        Stub stub = new Stub();

        new SchemaSelection("c0", "s0").apply(stub.connection(), false).close();

        assertEquals(List.of(), stub.calls, "no set, and so nothing to restore");
    }

    @Test
    void restoresSchemaBeforeCatalogAndOnlyWhatChanged() throws SQLException {
        Stub stub = new Stub();

        try (SchemaSelection.Scope scope = new SchemaSelection("c1", "s1").apply(stub.connection(), false)) {
            assertEquals("c1", stub.catalog);
            assertEquals("s1", stub.schema);
        }

        assertEquals(List.of("setCatalog:c1", "setSchema:s1", "setSchema:s0", "setCatalog:c0"), stub.calls);
    }

    @Test
    void aFailureAfterTheCatalogChangedPutsTheCatalogBack() {
        Stub stub = new Stub();
        stub.failingSchemas.add("s1");

        assertThrows(SQLException.class, () -> new SchemaSelection("c1", "s1").apply(stub.connection(), false));

        assertEquals("c0", stub.catalog);
        assertEquals("s0", stub.schema);
    }

    @Test
    void aFailedRestoreOnACallerConnectionIsThrownButNeverAborts() throws SQLException {
        Stub stub = new Stub();
        SchemaSelection.Scope scope = new SchemaSelection(null, "s1").apply(stub.connection(), false);
        stub.failingSchemas.add("s0");

        SQLException e = assertThrows(SQLException.class, scope::close);

        assertEquals("boom s0", e.getMessage());
        assertFalse(stub.aborted, "the caller's own connection must not be aborted");
    }

    @Test
    void aFailedRestoreOnAPooledConnectionAbortsItSoThePoolDiscardsIt() throws SQLException {
        Stub stub = new Stub();
        SchemaSelection.Scope scope = new SchemaSelection(null, "s1").apply(stub.connection(), true);
        stub.failingSchemas.add("s0");

        assertThrows(SQLException.class, scope::close);

        assertTrue(stub.aborted);
    }

    @Test
    void aFailingSchemaRestoreStillRestoresTheCatalog() throws SQLException {
        Stub stub = new Stub();
        SchemaSelection.Scope scope = new SchemaSelection("c1", "s1").apply(stub.connection(), false);
        stub.failingSchemas.add("s0");

        assertThrows(SQLException.class, scope::close);

        assertEquals("c0", stub.catalog);
    }

    @Test
    void aFailingSchemaAndCatalogRestoreReportBoth() throws SQLException {
        Stub stub = new Stub();
        SchemaSelection.Scope scope = new SchemaSelection("c1", "s1").apply(stub.connection(), true);
        stub.failingSchemas.add("s0");
        stub.failingCatalogs.add("c0");

        SQLException e = assertThrows(SQLException.class, scope::close);

        assertEquals("boom s0", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("boom c0", e.getSuppressed()[0].getMessage());
        assertTrue(stub.aborted);
    }

    @Test
    void aFailingCatalogRestoreAloneIsReported() throws SQLException {
        Stub stub = new Stub();
        SchemaSelection.Scope scope = new SchemaSelection("c1", null).apply(stub.connection(), false);
        stub.failingCatalogs.add("c0");

        SQLException e = assertThrows(SQLException.class, scope::close);

        assertEquals("boom c0", e.getMessage());
    }
}
