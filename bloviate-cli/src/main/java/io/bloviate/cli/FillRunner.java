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

import io.bloviate.db.DatabaseConfiguration;
import io.bloviate.db.DatabaseFiller;
import io.bloviate.db.SqlScript;
import io.bloviate.db.TableConfiguration;
import io.bloviate.ext.DatabaseSupport;
import io.bloviate.util.JdbcUrls;
import io.bloviate.util.MetadataPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Runs a resolved {@link FillPlan}: opens the connection through {@link DriverManager}, works out the
 * {@link DatabaseSupport}, and hands the fill to {@link DatabaseFiller}.
 *
 * <p>With one thread the fill runs on the connection opened here. With more, that connection is only
 * used to check the database and pick the support, and the fill borrows one connection per worker from a
 * {@link DriverManagerDataSource}.
 */
final class FillRunner {

    private static final Logger logger = LoggerFactory.getLogger(FillRunner.class);

    /** The most table names listed in an error message, so a huge schema does not flood it. */
    private static final int MAX_LISTED_TABLES = 20;

    private final FillPlan plan;

    FillRunner(FillPlan plan) {
        this.plan = plan;
    }

    /**
     * Fills the database.
     *
     * @throws ConnectionFailedException if the database cannot be reached
     * @throws SQLException              if the fill or a hook script fails
     * @throws IllegalArgumentException  if the table selection or a table configuration is invalid
     */
    void run() throws ConnectionFailedException, SQLException {
        String url = effectiveUrl(plan);
        Properties properties = connectionProperties(plan);
        logger.info("connecting to {}", Secrets.redactUrl(url));

        DatabaseSupport support;
        try (Connection connection = open(url, properties)) {
            support = plan.support() != null ? plan.support() : detectSupport(connection, url);
            logger.debug("filling with {}", support.getClass().getSimpleName());
            verifyTableRows(connection);

            if (plan.threads() == 1) {
                configure(new DatabaseFiller.Builder(connection, configuration(support))).build().fill();
                return;
            }
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, properties);
        configure(new DatabaseFiller.Builder(dataSource, configuration(support)).threads(plan.threads())).build().fill();
    }

    private static Connection open(String url, Properties properties) throws ConnectionFailedException {
        try {
            return DriverManager.getConnection(url, properties);
        } catch (SQLException e) {
            throw new ConnectionFailedException("cannot connect to " + Secrets.redactUrl(url), e);
        }
    }

    private static DatabaseSupport detectSupport(Connection connection, String url) throws ConnectionFailedException {
        try {
            return DatabaseSupport.forConnection(connection);
        } catch (SQLException e) {
            throw new ConnectionFailedException("cannot read the database product from " + Secrets.redactUrl(url), e);
        }
    }

    private static Properties connectionProperties(FillPlan plan) {
        Properties properties = new Properties();
        if (plan.user() != null) {
            properties.setProperty("user", plan.user());
        }
        if (plan.password() != null) {
            properties.setProperty("password", plan.password());
        }
        return properties;
    }

    private DatabaseConfiguration configuration(DatabaseSupport support) {
        Set<TableConfiguration> tableConfigurations = new HashSet<>();
        plan.tableRows().forEach((table, rows) -> tableConfigurations.add(new TableConfiguration(table, rows)));
        return new DatabaseConfiguration.Builder(plan.batchSize(), plan.rows(), support)
                .seed(plan.seed())
                .tableConfigurations(tableConfigurations)
                .commitStrategy(plan.commit())
                .bulkLoadStrategy(plan.bulkLoad())
                .build();
    }

    /** Applies everything the plan says about what to fill and what to run around it. */
    private DatabaseFiller.Builder configure(DatabaseFiller.Builder builder) {
        if (plan.schema() != null) {
            builder.schema(plan.schema());
        }
        if (plan.catalog() != null) {
            builder.catalog(plan.catalog());
        }
        builder.includeTables(plan.include()).excludeTables(plan.exclude());
        plan.before().forEach(script -> builder.before(SqlScript.file(script)));
        plan.after().forEach(script -> builder.after(SqlScript.file(script)));
        return builder;
    }

    /**
     * The URL to connect with: the plan's, plus the driver's batch-rewrite parameter when it is a
     * PostgreSQL, MySQL or MariaDB URL that does not already set it. The database is judged by the URL
     * prefix, because nothing has been connected to yet; a URL the parameter does not apply to, or that
     * already carries it (with any value), is never touched.
     *
     * @param plan the plan
     * @return the URL to connect with
     */
    static String effectiveUrl(FillPlan plan) {
        if (!plan.batchRewrite()) {
            return plan.url();
        }
        String product = productOf(plan.url());
        if (product == null) {
            return plan.url();
        }
        DatabaseSupport support = plan.support() != null ? plan.support() : DatabaseSupport.forProduct(product);
        String parameter = support.batchRewriteUrlParameter();
        String url = JdbcUrls.withBatchRewrite(plan.url(), parameter);
        if (!url.equals(plan.url())) {
            logger.info("appended {}=true to the JDBC URL to batch inserts in the driver (disable with --no-batch-rewrite)",
                    parameter);
        }
        return url;
    }

    /** The product name for the URLs whose driver has a batch-rewrite parameter; null for any other. */
    private static String productOf(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:postgresql:")) {
            return "PostgreSQL";
        }
        if (lower.startsWith("jdbc:mysql:")) {
            return "MySQL";
        }
        if (lower.startsWith("jdbc:mariadb:")) {
            return "MariaDB";
        }
        return null;
    }

    /**
     * Rejects a {@code --table-rows} name that is not a table of the selected schema. The core only
     * warns about it, and a typo there means the table is silently filled with the default row count.
     *
     * <p>Skipped when {@code --before} scripts are given: they can create the tables, so the schema is
     * not final until they have run, and the core (which reads it after them) can only warn.
     */
    private void verifyTableRows(Connection connection) {
        if (plan.tableRows().isEmpty()) {
            return;
        }
        if (!plan.before().isEmpty()) {
            logger.debug("not checking --table-rows names up front: --before scripts may create the tables");
            return;
        }
        List<String> tables = new ArrayList<>();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = plan.catalog() != null ? plan.catalog() : connection.getCatalog();
            String schema = plan.schema() != null ? plan.schema() : connection.getSchema();
            // the schema is a LIKE pattern here, so it is escaped to match only itself
            MetadataPatterns patterns = MetadataPatterns.forMetaData(metaData);
            try (ResultSet resultSet = metaData.getTables(catalog, patterns.literal(schema), null, new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            // best effort: the fill itself reports a schema it cannot read
            logger.debug("could not list tables to check --table-rows: {}", e.getMessage());
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (String name : plan.tableRows().keySet()) {
            if (tables.stream().noneMatch(table -> table.equalsIgnoreCase(name))) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            unknown.sort(String.CASE_INSENSITIVE_ORDER);
            throw new IllegalArgumentException("--table-rows names no table in the selected schema: " + unknown
                    + " (found: " + listed(tables) + ")");
        }
    }

    private static String listed(List<String> tables) {
        List<String> sorted = new ArrayList<>(tables);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        if (sorted.size() <= MAX_LISTED_TABLES) {
            return sorted.toString();
        }
        return sorted.subList(0, MAX_LISTED_TABLES) + " and " + (sorted.size() - MAX_LISTED_TABLES) + " more";
    }
}
