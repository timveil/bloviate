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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A minimal fixed-size connection pool for tests that must observe what a fill leaves on the
 * <em>physical</em> connections it borrowed. Real pools (HikariCP) reset some session state, such as
 * the schema, when a connection is returned, which would hide exactly the leak these tests look for;
 * this one hands the same physical connections out again untouched. {@link Connection#close()} on a
 * borrowed connection returns it to the pool.
 */
final class TrackingDataSource implements DataSource, AutoCloseable {

    private final List<Connection> physical = new ArrayList<>();
    private final BlockingQueue<Connection> free;

    TrackingDataSource(String url, String user, String password, int size) throws SQLException {
        free = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            Connection connection = user == null
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, user, password);
            physical.add(connection);
            free.add(connection);
        }
    }

    /** The physical connections, for inspecting their state after a fill. */
    List<Connection> physicalConnections() {
        return List.copyOf(physical);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection;
        try {
            // a fill that pins a connection it should have returned would starve here rather than hang
            connection = free.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("interrupted waiting for a connection", e);
        }
        if (connection == null) {
            throw new SQLException("no connection became free within 5 seconds");
        }
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        if (!free.contains(connection)) {
                            free.add(connection);
                        }
                        return null;
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public void close() throws SQLException {
        for (Connection connection : physical) {
            connection.close();
        }
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // not used
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // not used
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }
}
