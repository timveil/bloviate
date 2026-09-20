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

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverManagerDataSourceTest {

    private static Properties credentials(String user, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return properties;
    }

    @Test
    void everyCallOpensAnIndependentConnectionTheCallerCloses() throws SQLException {
        try (H2Database database = H2Database.named("cli_ds", "sa", "pw", "create table t (id int)")) {
            DataSource dataSource = new DriverManagerDataSource(database.url(), credentials("sa", "pw"));

            Connection first = dataSource.getConnection();
            Connection second = dataSource.getConnection();
            assertNotSame(first, second);
            first.close();
            assertTrue(first.isClosed());
            assertFalse(second.isClosed(), "closing one connection must not touch another");
            second.close();
        }
    }

    @Test
    void explicitCredentialsOverrideTheConfiguredOnes() throws SQLException {
        try (H2Database database = H2Database.named("cli_ds_creds", "sa", "pw", "create table t (id int)")) {
            DataSource dataSource = new DriverManagerDataSource(database.url(), credentials("someone", "wrong"));

            assertThrows(SQLException.class, dataSource::getConnection);
            try (Connection connection = dataSource.getConnection("sa", "pw")) {
                assertFalse(connection.isClosed());
            }
        }
    }

    @Test
    void aFailureToConnectIsTheDriversSqlException() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:nosuchdb:x", new Properties());

        assertThrows(SQLException.class, dataSource::getConnection);
    }

    @Test
    void itIsAPlainDataSourceWithNoLoggerOfItsOwn() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:x", new Properties());

        assertSame(dataSource, dataSource.unwrap(DataSource.class));
        assertTrue(dataSource.isWrapperFor(DataSource.class));
        assertFalse(dataSource.isWrapperFor(String.class));
        assertThrows(SQLException.class, () -> dataSource.unwrap(String.class));
        assertThrows(SQLFeatureNotSupportedException.class, dataSource::getParentLogger);
        dataSource.setLoginTimeout(dataSource.getLoginTimeout());
        dataSource.setLogWriter(dataSource.getLogWriter());
    }
}
