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

import io.bloviate.ext.BigQuerySupport;
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Fills a real BigQuery dataset. There is no emulator — the tbc-bq-jdbc driver deliberately dropped
 * its emulator tier because the emulator's semantics diverged far enough to hide real defects — so
 * this test needs a live Google Cloud project and is skipped by default.
 *
 * <p>It is gated twice, and both gates must pass for it to run:
 * <ol>
 *   <li>{@code BLOVIATE_BQ_PROJECT} is set (and Application Default Credentials are available); and</li>
 *   <li>the driver is on the classpath, which only happens under {@code -Pbigquery}.</li>
 * </ol>
 * The second gate matters on its own: with the env var set but the profile off, the test skips
 * rather than failing with "No suitable driver". Nothing here imports a driver class, so it
 * compiles in every build.
 *
 * <pre>
 * ./mvnw clean install                        # once, in the tbc-bq-jdbc repo
 * gcloud auth application-default login
 * export BLOVIATE_BQ_PROJECT=my-gcp-project
 * ./mvnw verify -Pbigquery -pl bloviate-core -Dtest=BigQueryFillerTest
 * </pre>
 *
 * <p>Each run creates its own dataset and drops it afterwards, because {@link DatabaseFiller} fills
 * <em>every</em> table it finds in the connection's schema. The dataset also carries a one-day
 * default table expiration so an aborted run cannot leave billable tables behind.
 */
@EnabledIf("bigQueryAvailable")
class BigQueryFillerTest extends BaseDatabaseTestCase {

    private static final String PROJECT_ENV = "BLOVIATE_BQ_PROJECT";
    private static final String DRIVER_CLASS = "vc.tbc.bq.jdbc.BQDriver";

    private static final int BATCH_SIZE = 500;
    private static final long ROW_COUNT = 25;

    @SuppressWarnings("unused") // referenced by @EnabledIf
    static boolean bigQueryAvailable() {
        if (System.getenv(PROJECT_ENV) == null) {
            return false;
        }
        try {
            Class.forName(DRIVER_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Test
    void fillsScalarTypesAndFollowsUnenforcedForeignKeys() throws SQLException {
        String project = System.getenv(PROJECT_ENV);
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        String dataset = "bloviate_it_" + runId;
        String url = String.format("jdbc:bigquery:%s/%s?authType=ADC", project, dataset);

        DatabaseConfiguration configuration = new DatabaseConfiguration.Builder(
                BATCH_SIZE, ROW_COUNT, new BigQuerySupport()).build();

        try (Connection connection = DriverManager.getConnection(url)) {
            createDataset(connection, project, dataset);
            try {
                runScript(connection, "create_tables.bigquery.sql",
                        Map.of("dataset", dataset, "suffix", runId));

                assertUnenforcedForeignKeysAreVisible(connection, runId);

                new DatabaseFiller.Builder(connection, configuration).build().fill();

                verify(connection, dataset, runId);
            } finally {
                dropDataset(connection, project, dataset);
            }
        }
    }

    private static void createDataset(Connection connection, String project, String dataset) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(String.format(
                    "CREATE SCHEMA IF NOT EXISTS `%s.%s` OPTIONS(default_table_expiration_days = 1)",
                    project, dataset));
        }
    }

    private static void dropDataset(Connection connection, String project, String dataset) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(String.format("DROP SCHEMA IF EXISTS `%s.%s` CASCADE", project, dataset));
        }
    }

    /**
     * The single highest-value assertion here: BigQuery's keys are always {@code NOT ENFORCED}, and
     * this proves the driver still surfaces them through {@code getImportedKeys} so Bloviate's
     * dependency graph and foreign-key value alignment have something to work with. Asserted before
     * the fill so a metadata regression is not mistaken for a data problem.
     */
    private static void assertUnenforcedForeignKeysAreVisible(Connection connection, String runId) throws SQLException {
        Database database = DatabaseUtils.getMetadata(connection);
        Table nation = database.getTable("nation_" + runId);

        assertFalse(nation.foreignKeys().isEmpty(),
                "expected a NOT ENFORCED foreign key on nation_" + runId + " to be visible via getImportedKeys");
    }

    private static void verify(Connection connection, String dataset, String runId) throws SQLException {
        String region = String.format("`%s.region_%s`", dataset, runId);
        String nation = String.format("`%s.nation_%s`", dataset, runId);
        String standard = String.format("`%s.standard_types_%s`", dataset, runId);
        String events = String.format("`%s.events_%s`", dataset, runId);

        assertRowCount(connection, region, ROW_COUNT);
        assertRowCount(connection, nation, ROW_COUNT);
        assertRowCount(connection, standard, ROW_COUNT);
        assertRowCount(connection, events, ROW_COUNT);

        // foreign-key columns are seeded from their parent primary-key column, so every child value
        // must resolve even though BigQuery never enforces the constraint
        assertCount(connection, String.format(
                "select count(*) from %s n left join %s r on n.n_regionkey = r.r_regionkey where r.r_regionkey is null",
                nation, region), 0);

        // the clamps held against the real service: a bare STRING column reports a 2 MB maximum and
        // a bare NUMERIC reports scale 9, neither of which should reach the wire unmodified
        assertCount(connection, String.format(
                "select count(*) from %s where length(c_string) > %d",
                standard, BigQuerySupport.MAX_STRING_LENGTH), 0);
        assertCount(connection, String.format(
                "select count(*) from %s where length(c_string_sized) > 20", standard), 0);
        assertCount(connection, String.format(
                "select count(*) from %s where length(c_bytes) > %d",
                standard, BigQuerySupport.MAX_BYTES_LENGTH), 0);

        // BIGNUMERIC is bound as NUMERIC, so its values must sit inside NUMERIC's range
        assertCount(connection, String.format(
                "select count(*) from %s where abs(c_bignumeric) >= 1e29", standard), 0);
    }
}
