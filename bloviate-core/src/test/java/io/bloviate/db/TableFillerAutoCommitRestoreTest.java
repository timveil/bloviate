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
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the {@link TableFiller} autocommit-restore failure path (issue #526, L2): when the engine
 * manages the transaction and restoring the connection's prior autocommit setting throws, that failure
 * must surface rather than be swallowed, and the connection — now in an unknown transaction state —
 * must be aborted so a pool discards it instead of reusing it.
 *
 * <p>Uses an in-memory H2 connection wrapped in a proxy that fails only the {@code setAutoCommit(true)}
 * restore (the initial {@code setAutoCommit(false)} and all other calls delegate normally), so no
 * container is needed.
 */
class TableFillerAutoCommitRestoreTest extends BaseEmbeddedTest {

    @Test
    void abortsConnectionAndSurfacesFailureWhenAutoCommitRestoreFails() throws SQLException {
        try (Connection real = DriverManager.getConnection("jdbc:h2:mem:autocommit_restore;DB_CLOSE_DELAY=-1")) {
            try (Statement statement = real.createStatement()) {
                statement.execute("create table widget (id integer)");
            }

            Database database = DatabaseUtils.getMetadata(real);
            Table table = database.getTable("widget");

            AtomicBoolean aborted = new AtomicBoolean(false);
            Connection proxy = throwingOnAutoCommitRestore(real, aborted);

            // perTable => the engine manages the transaction (autocommit off, commit, then restore)
            DatabaseConfiguration configuration =
                    new DatabaseConfiguration(16, 5, new H2Support(), null, 1L, CommitStrategy.perTable());

            // the fill itself succeeds, but restoring autocommit throws — that failure must surface
            SQLException thrown = assertThrows(SQLException.class, () ->
                    new TableFiller.Builder(proxy, database, configuration)
                            .table(table)
                            .commitStrategy(CommitStrategy.perTable())
                            .build().fill());

            assertTrue(thrown.getMessage().contains("simulated"), "the restore failure should surface, not be swallowed");
            assertTrue(aborted.get(), "the connection must be aborted when its autocommit cannot be restored");
        }
    }

    /**
     * Wraps {@code real} so {@code setAutoCommit(true)} (the restore) throws while
     * {@code setAutoCommit(false)} and every other call delegate normally; records whether
     * {@link Connection#abort} was invoked.
     */
    private static Connection throwingOnAutoCommitRestore(Connection real, AtomicBoolean aborted) {
        return (Connection) Proxy.newProxyInstance(
                TableFillerAutoCommitRestoreTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> {
                            if (Boolean.TRUE.equals(args[0])) {
                                throw new SQLException("simulated autocommit restore failure");
                            }
                            return invoke(method, real, args);
                        }
                        case "abort" -> {
                            aborted.set(true);
                            return null; // record only; leave the in-memory connection usable for cleanup
                        }
                        default -> {
                            return invoke(method, real, args);
                        }
                    }
                });
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
