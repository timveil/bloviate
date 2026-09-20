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

package io.bloviate.cli;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The smallest {@link DataSource} that satisfies the parallel fill: every {@link #getConnection()} opens
 * a fresh connection through {@link DriverManager}, and the caller closes it. There is no pool, because a
 * fill borrows one connection per worker for the length of a table and never needs to reuse one, so a
 * pooling dependency would buy nothing here.
 */
final class DriverManagerDataSource implements DataSource {

    private final String url;
    private final Properties properties;

    /**
     * Creates a data source.
     *
     * @param url        the JDBC URL every connection is opened with
     * @param properties the connection properties ({@code user}, {@code password}); copied
     */
    DriverManagerDataSource(String url, Properties properties) {
        this.url = url;
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    @Override
    public Connection getConnection() throws SQLException {
        // a copy per call: a driver is free to add to the properties it is given
        Properties copy = new Properties();
        copy.putAll(properties);
        return DriverManager.getConnection(url, copy);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties copy = new Properties();
        copy.putAll(properties);
        copy.setProperty("user", username);
        copy.setProperty("password", password);
        return DriverManager.getConnection(url, copy);
    }

    @Override
    public PrintWriter getLogWriter() {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        DriverManager.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("DriverManagerDataSource does not use java.util.logging");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
