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

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.TruncatedDateGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #619, against a real PostgreSQL: first-of-month {@code CHECK}s written with
 * {@code date_trunc} and with {@code EXTRACT}, on {@code DATE}, {@code TIMESTAMP} and
 * {@code TIMESTAMP WITH TIME ZONE} columns, fill with zero violations &mdash; from the constraint
 * alone and with an explicit generator override, and in session time zones other than UTC.
 *
 * <p>The time zone matters only for {@code timestamptz}: {@code date_trunc('month', x)} is evaluated in
 * the <em>session</em> zone, so "midnight on the first" has to mean midnight in that zone, not in UTC or
 * the JVM's zone. Every fill therefore runs in a session whose zone is set explicitly, and the
 * violation counts are taken on that same connection, in that zone (the constraint is checked at
 * insert time, so a later read in a different zone would say nothing about it).
 */
class PostgresDateCheckFillTest extends BaseDatabaseTestCase {

    private static final int ROWS = 300;
    private static final long SEED = 42L;

    /** UTC, plus zones east and west of it and one with a half-hour offset. */
    private static final List<String> ZONES = List.of("UTC", "Pacific/Auckland", "America/Los_Angeles", "Asia/Kolkata");

    private static final List<String> MONTH_DATE_COLUMNS = List.of("d_trunc", "d_extract", "d_quarter");
    private static final List<String> MONTH_TIMESTAMP_COLUMNS =
            List.of("ts_trunc", "ts_extract", "tstz_trunc", "tstz_extract", "tstz_year");

    private static PostgresSchemaFixture fixture;

    @BeforeAll
    static void startDatabase() {
        fixture = new PostgresSchemaFixture("create_date_checks.postgres.sql");
    }

    @AfterAll
    static void stopDatabase() {
        fixture.close();
    }

    @BeforeEach
    void emptyTables() throws SQLException {
        fixture.reset("public");
    }

    private static DatabaseConfiguration configuration(Set<TableConfiguration> tables) {
        return new DatabaseConfiguration(64, ROWS, new PostgresSupport(), tables, SEED);
    }

    /** The same fill for every zone, so the CHECK is enforced by the server in that zone at insert time. */
    private static void fillIn(String zone, DatabaseConfiguration configuration, Verifier verifier) throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("public");
             Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET TIME ZONE '" + zone + "'");
            }
            new DatabaseFiller.Builder(connection, configuration).build().fill();
            verifier.verify(connection);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // from the constraint alone
    // ---------------------------------------------------------------------------------------------

    @Test
    void firstOfMonthChecksAreSatisfiedInEverySessionTimeZone() throws SQLException {
        for (String zone : ZONES) {
            fixture.reset("public");
            fillIn(zone, configuration(null), connection -> {
                assertEquals(ROWS, count(connection, "select count(*) from month_dates"), zone);
                assertNoMonthDateViolations(connection, zone);
                assertVariety(connection, zone);
                assertMidnightInSessionZone(connection, zone);
            });
        }
    }

    @Test
    void firstOfMonthChecksAreSatisfiedAlongsideEnumInAndBetweenChecks() throws SQLException {
        for (String zone : ZONES) {
            fixture.reset("public");
            fillIn(zone, configuration(null), connection -> {
                assertEquals(ROWS, count(connection, "select count(*) from mixed_checks"), zone);
                assertEquals(0, count(connection, "select count(*) from mixed_checks where "
                        + "date_trunc('month', billing_month) <> billing_month or extract(day from posted_at) <> 1"), zone);
                assertEquals(0, count(connection, "select count(*) from mixed_checks where state::text not in ('OPEN','HELD','CLOSED')"), zone);
                assertEquals(0, count(connection, "select count(*) from mixed_checks where rating < 1 or rating > 5"), zone);
                assertEquals(0, count(connection, "select count(*) from mixed_checks where priority not in (1,2,3)"), zone);
                assertEquals(0, count(connection, "select count(*) from mixed_checks where grade not in ('A','B','C','D','F')"), zone);
                assertEquals(0, count(connection, "select count(*) from mixed_checks where amount < 0 or amount > 9999.99"), zone);
                assertTrue(count(connection, "select count(distinct billing_month) from mixed_checks") > 12, zone);
                assertTrue(count(connection, "select count(distinct state) from mixed_checks") > 1, zone);
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // with an explicit generator override
    // ---------------------------------------------------------------------------------------------

    @Test
    void firstOfMonthChecksAreSatisfiedWithExplicitGenerators() throws SQLException {
        Set<ColumnConfiguration> columns = new java.util.HashSet<>();
        for (String column : MONTH_DATE_COLUMNS) {
            TruncatedDateGenerator.Unit unit = column.equals("d_quarter")
                    ? TruncatedDateGenerator.Unit.QUARTER : TruncatedDateGenerator.Unit.MONTH;
            columns.add(new ColumnConfiguration(column, random -> new TruncatedDateGenerator.Builder(random).unit(unit).build()));
        }
        for (String column : MONTH_TIMESTAMP_COLUMNS) {
            TruncatedDateGenerator.Unit unit = column.equals("tstz_year")
                    ? TruncatedDateGenerator.Unit.YEAR : TruncatedDateGenerator.Unit.MONTH;
            columns.add(new ColumnConfiguration(column,
                    random -> new TruncatedDateGenerator.Builder(random).unit(unit).timestamp(true).build()));
        }
        DatabaseConfiguration configuration = configuration(Set.of(new TableConfiguration("month_dates", ROWS, columns)));

        for (String zone : ZONES) {
            fixture.reset("public");
            fillIn(zone, configuration, connection -> {
                assertEquals(ROWS, count(connection, "select count(*) from month_dates"), zone);
                assertNoMonthDateViolations(connection, zone);
                assertMidnightInSessionZone(connection, zone);
            });
        }
    }

    @Test
    void anExplicitGeneratorWinsOverTheConstraint() throws SQLException {
        // pin billing_month to one narrow window: the override must be used, not the default window
        Set<ColumnConfiguration> columns = Set.of(new ColumnConfiguration("billing_month", random ->
                new TruncatedDateGenerator.Builder(random)
                        .start(java.time.LocalDate.of(2031, 1, 1)).end(java.time.LocalDate.of(2031, 3, 1)).build()));

        fillIn("UTC", configuration(Set.of(new TableConfiguration("mixed_checks", ROWS, columns))), connection -> {
            assertEquals(0, count(connection, "select count(*) from mixed_checks "
                    + "where billing_month not in (date '2031-01-01', date '2031-02-01')"));
            assertEquals(2, count(connection, "select count(distinct billing_month) from mixed_checks"));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // reproducibility
    // ---------------------------------------------------------------------------------------------

    @Test
    void theSameSeedProducesTheSameRowsInASessionZone() throws SQLException {
        String query = "select md5(string_agg(t::text, ',' order by id)) from month_dates t";
        String[] first = new String[1];
        fillIn("Pacific/Auckland", configuration(null), connection -> first[0] = text(connection, query));

        fixture.reset("public");
        fillIn("Pacific/Auckland", configuration(null), connection ->
                assertEquals(first[0], text(connection, query), "same seed, same zone, same data"));
    }

    // ---------------------------------------------------------------------------------------------
    // assertions
    // ---------------------------------------------------------------------------------------------

    private static void assertNoMonthDateViolations(Connection connection, String zone) throws SQLException {
        // evaluated in the session zone the rows were inserted in
        assertEquals(0, count(connection, "select count(*) from month_dates where "
                + "date_trunc('month', d_trunc) <> d_trunc or extract(day from d_extract) <> 1 "
                + "or date_trunc('month', ts_trunc) <> ts_trunc or extract(day from ts_extract) <> 1 "
                + "or date_trunc('month', tstz_trunc) <> tstz_trunc or extract(day from tstz_extract) <> 1 "
                + "or date_trunc('quarter', d_quarter) <> d_quarter or date_trunc('year', tstz_year) <> tstz_year"), zone);
    }

    private static void assertVariety(Connection connection, String zone) throws SQLException {
        for (String column : List.of("d_trunc", "d_extract", "ts_trunc", "tstz_trunc")) {
            assertTrue(count(connection, "select count(distinct " + column + ") from month_dates") > 12,
                    zone + ": " + column + " should span many months");
        }
        assertTrue(count(connection, "select count(distinct d_quarter) from month_dates") > 4, zone);
        assertTrue(count(connection, "select count(distinct tstz_year) from month_dates") > 3, zone);
    }

    /**
     * The proof that a timestamptz value is midnight in the session zone rather than in UTC: for any
     * zone other than UTC the stored instant is not midnight UTC, yet the check (already enforced at
     * insert) holds.
     */
    private static void assertMidnightInSessionZone(Connection connection, String zone) throws SQLException {
        long midnightUtc = count(connection, "select count(*) from month_dates where "
                + "(tstz_trunc at time zone 'UTC')::time = time '00:00'");
        assertEquals("UTC".equals(zone) ? ROWS : 0, midnightUtc, zone + ": rows whose instant is midnight UTC");
        assertEquals(ROWS, count(connection, "select count(*) from month_dates where "
                + "(tstz_trunc at time zone current_setting('TimeZone'))::time = time '00:00'"), zone);
    }

    private static long count(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String text(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
