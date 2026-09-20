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

import io.bloviate.db.SqlScript;
import io.bloviate.db.SqlScriptRunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An in-memory H2 database for the CLI tests: a unique name, kept alive by an anchoring connection
 * (and by {@code DB_CLOSE_DELAY=-1}, so the CLI closing its own connection does not drop it), with a
 * few helpers to set it up and read it back.
 */
final class H2Database implements AutoCloseable {

    /** A parent/child pair plus a derived table an {@code --after} script would compute. */
    static final String ORDERS_SCHEMA = """
            create table customers (id int primary key, name varchar(30), region varchar(10));
            create table orders (id int primary key, customer_id int not null, amount int not null,
                foreign key (customer_id) references customers (id));
            create table order_stats (region varchar(10), total bigint, n bigint);
            """;

    private final String name;
    private final String user;
    private final String password;
    private final Connection anchor;

    private H2Database(String name, String user, String password) throws SQLException {
        this.name = name;
        this.user = user;
        this.password = password;
        // the first connection to a new in-memory database creates it, and its credentials become the
        // database's administrator
        this.anchor = user == null ? DriverManager.getConnection(url()) : DriverManager.getConnection(url(), user, password);
    }

    /**
     * Creates a database with a unique name and runs {@code schema} in it.
     *
     * @param schema DDL to run
     * @return the database
     * @throws SQLException if the DDL fails
     */
    static H2Database create(String schema) throws SQLException {
        return named("cli_" + UUID.randomUUID().toString().replace("-", ""), null, null, schema);
    }

    /**
     * Creates a database with a chosen name (the name is part of the seed, so a test that compares two
     * fills recreates the same name) and credentials.
     *
     * @param name     the in-memory database name
     * @param user     the administrator user, or null for none
     * @param password the administrator's password
     * @param schema   DDL to run
     * @return the database
     * @throws SQLException if the DDL fails
     */
    static H2Database named(String name, String user, String password, String schema) throws SQLException {
        H2Database database = new H2Database(name, user, password);
        database.execute(schema);
        return database;
    }

    String url() {
        return "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
    }

    void execute(String sql) throws SQLException {
        SqlScriptRunner.run(anchor, SqlScript.inline("test", sql));
    }

    long count(String table) throws SQLException {
        return scalar("select count(*) from " + table);
    }

    long scalar(String sql) throws SQLException {
        try (Statement statement = anchor.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /** Every row of a query as one {@code |}-joined string, in the query's order. */
    List<String> rows(String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement statement = anchor.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                StringBuilder row = new StringBuilder();
                for (int column = 1; column <= metaData.getColumnCount(); column++) {
                    row.append(column == 1 ? "" : "|").append(resultSet.getString(column));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    /** The user to pass to the CLI, or null. */
    String user() {
        return user;
    }

    String password() {
        return password;
    }

    /** Drops the database, so the same name can be created again empty. */
    @Override
    public void close() throws SQLException {
        try (Statement statement = anchor.createStatement()) {
            statement.execute("SHUTDOWN");
        } finally {
            anchor.close();
        }
    }
}
