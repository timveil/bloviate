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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The catalog and schema a fill works in, set with {@link DatabaseFiller.Builder#catalog(String)} and
 * {@link DatabaseFiller.Builder#schema(String)}, and the means of putting them on a connection and
 * taking them off again.
 *
 * <p>Selection is applied to the {@link Connection} itself ({@link Connection#setCatalog} /
 * {@link Connection#setSchema}) rather than only passed to the metadata queries, because more than
 * metadata discovery depends on it: an unqualified table name in a hook script resolves against the
 * connection's schema, and a driver that reports no schema (MySQL, MariaDB) is filled through the
 * connection's catalog. {@link #apply} therefore runs on every connection the fill uses, and the
 * returned {@link Scope} puts the previous values back when closed, so a connection borrowed from a
 * pool is never returned still pointing at another schema.
 *
 * <p>A driver that does not support the requested selection is an error, not a silent no-op: after
 * setting, the connection must report the requested value back, otherwise {@link #apply} throws.
 * That also catches a schema that does not exist (PostgreSQL's {@code setSchema} succeeds on a
 * missing schema, but {@code getSchema} then reports a different one).
 *
 * @since 3.3.0
 */
final class SchemaSelection {

    private static final Logger logger = LoggerFactory.getLogger(SchemaSelection.class);

    /** Leaves the connection as it is. */
    static final SchemaSelection NONE = new SchemaSelection(null, null);

    private static final Scope NO_OP = () -> { };

    private final String catalog;
    private final String schema;

    /**
     * Creates a selection.
     *
     * @param catalog the catalog to select, or null to leave the connection's catalog alone
     * @param schema  the schema to select, or null to leave the connection's schema alone
     */
    SchemaSelection(String catalog, String schema) {
        this.catalog = catalog;
        this.schema = schema;
    }

    /** True when a catalog or a schema is selected. */
    boolean isSet() {
        return catalog != null || schema != null;
    }

    /** A connection state change that {@link #close()} undoes. */
    @FunctionalInterface
    interface Scope extends AutoCloseable {
        /**
         * Puts the connection's catalog and schema back as they were before {@link #apply}.
         *
         * @throws SQLException if the previous values cannot be restored
         */
        @Override
        void close() throws SQLException;
    }

    /**
     * Selects the catalog and schema on {@code connection}.
     *
     * @param connection the connection to change
     * @param pooled     true for a connection borrowed from a pool: if it cannot be restored it is
     *                   {@link Connection#abort aborted} so the pool discards it instead of handing it
     *                   on pointing at the wrong schema. A caller-owned connection is never aborted.
     * @return a scope that restores the connection's previous catalog and schema when closed; it does
     *         nothing when nothing is selected
     * @throws SQLException if the driver rejects the selection or does not report it back
     */
    Scope apply(Connection connection, boolean pooled) throws SQLException {
        if (!isSet()) {
            return NO_OP;
        }

        String previousCatalog = null;
        String previousSchema = null;
        boolean catalogChanged = false;
        boolean schemaChanged = false;

        try {
            if (catalog != null) {
                previousCatalog = connection.getCatalog();
                if (!catalog.equals(previousCatalog)) {
                    catalogChanged = true;
                    connection.setCatalog(catalog);
                    verify("catalog", catalog, connection.getCatalog(), "on PostgreSQL a connection cannot switch database");
                }
            }
            if (schema != null) {
                previousSchema = connection.getSchema();
                if (!schema.equals(previousSchema)) {
                    schemaChanged = true;
                    connection.setSchema(schema);
                    verify("schema", schema, connection.getSchema(), "on MySQL/MariaDB a database is a catalog, use catalog(...)");
                }
            }
        } catch (SQLException e) {
            try {
                restore(connection, pooled, catalogChanged ? previousCatalog : null, schemaChanged ? previousSchema : null);
            } catch (SQLException restoreFailure) {
                e.addSuppressed(restoreFailure);
            }
            throw e;
        }

        String catalogToRestore = catalogChanged ? previousCatalog : null;
        String schemaToRestore = schemaChanged ? previousSchema : null;
        logger.debug("selected catalog [{}] schema [{}] on a {} connection", catalog, schema, pooled ? "pooled" : "caller-owned");
        return () -> restore(connection, pooled, catalogToRestore, schemaToRestore);
    }

    /** Fails unless the connection reports the value that was just set. */
    private static void verify(String what, String requested, String actual, String hint) throws SQLException {
        if (!requested.equalsIgnoreCase(actual)) {
            throw new SQLException(String.format(
                    "cannot select %s [%s]: the connection reports %s [%s] after setting it. The %s may not exist, "
                            + "or this driver does not support selecting it (%s)",
                    what, requested, what, actual, what, hint));
        }
    }

    /**
     * Puts back the previous values. A null value means "not changed, or not reported by the driver",
     * so there is nothing to put back. The schema is restored before the catalog, the reverse of how
     * they were set, and a failure in one does not skip the other.
     */
    private static void restore(Connection connection, boolean pooled, String catalog, String schema) throws SQLException {
        SQLException failure = null;
        try {
            if (schema != null) {
                connection.setSchema(schema);
            }
        } catch (SQLException e) {
            failure = e;
        }
        try {
            if (catalog != null) {
                connection.setCatalog(catalog);
            }
        } catch (SQLException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            if (pooled) {
                logger.error("failed to restore the catalog/schema on a pooled connection; aborting it so it is "
                        + "not returned to the pool pointing at the wrong schema", failure);
                try {
                    connection.abort(Runnable::run);
                } catch (SQLException abortFailure) {
                    failure.addSuppressed(abortFailure);
                }
            }
            throw failure;
        }
    }
}
