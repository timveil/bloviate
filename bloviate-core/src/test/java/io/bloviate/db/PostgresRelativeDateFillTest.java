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
import io.bloviate.gen.RelativeWindow;
import io.bloviate.gen.SqlDateGenerator;
import io.bloviate.gen.SqlTimestampGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #616 against a real PostgreSQL: the motivating case. Tables whose dates only admit one quarter
 * (a range {@code CHECK}, a range partition) are filled validly by a window relative to a pinned
 * {@code asOf}; the same seed and anchor reproduce the same rows, another anchor moves them, and a
 * parallel, partitioned fill uses a single anchor.
 */
class PostgresRelativeDateFillTest extends BaseDatabaseTestCase {

    private static final long SEED = 42L;
    private static final int ROWS = 300;
    // the CHECKs and the partition admit [2026-01-01, 2026-04-01), which is exactly 90 days back from this
    private static final Instant AS_OF = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant Q1_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final RelativeWindow LAST_90_DAYS = RelativeWindow.withinLast("90d");
    private static final RelativeWindow LAST_10_DAYS = RelativeWindow.withinLast("10d");

    private static PostgresSchemaFixture fixture;

    @BeforeAll
    static void startDatabase() {
        fixture = new PostgresSchemaFixture("create_relative_dates.postgres.sql");
    }

    @AfterAll
    static void stopDatabase() {
        fixture.close();
    }

    @BeforeEach
    void emptyTables() throws SQLException {
        fixture.reset("public");
    }

    private static ColumnConfiguration timestampWithin(String column, RelativeWindow window) {
        return ColumnConfiguration.relative(column, window,
                (random, resolved) -> new SqlTimestampGenerator.Builder(random).window(resolved).build());
    }

    private static ColumnConfiguration dateWithin(String column, RelativeWindow window) {
        return ColumnConfiguration.relative(column, window,
                (random, resolved) -> new SqlDateGenerator.Builder(random).window(resolved).build());
    }

    /** Every table of the schema, each date column drawn from a window relative to the anchor. */
    private static DatabaseConfiguration configuration(int eventPartitions) {
        return new DatabaseConfiguration.Builder(64, ROWS, new PostgresSupport())
                .seed(SEED)
                .tableConfigurations(Set.of(
                        new TableConfiguration("orders", ROWS, Set.of(
                                timestampWithin("placed_at", LAST_90_DAYS), dateWithin("due_on", LAST_90_DAYS))),
                        new TableConfiguration("order_notes", ROWS, Set.of(timestampWithin("noted_at", LAST_10_DAYS))),
                        new TableConfiguration("ledger_2026_q1", ROWS, Set.of(timestampWithin("booked_at", LAST_90_DAYS))),
                        new TableConfiguration("events", 2_000, Set.of(
                                timestampWithin("happened_at", LAST_90_DAYS), dateWithin("happened_on", LAST_90_DAYS)),
                                eventPartitions),
                        new TableConfiguration("audit", 2_000, Set.of(timestampWithin("audited_at", LAST_90_DAYS)),
                                eventPartitions)))
                .build();
    }

    private static void fillSequential(DatabaseFiller.Builder builder) throws SQLException {
        builder.build().fill();
    }

    private static List<String> epochMillis(Connection connection, String column, String table) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select id, (extract(epoch from " + column + ") * 1000)::bigint from "
                     + table + " order by id")) {
            while (rs.next()) {
                values.add(rs.getInt(1) + "|" + rs.getLong(2));
            }
        }
        return values;
    }

    private static List<String> days(Connection connection, String column, String table) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select id, " + column + "::text from " + table + " order by id")) {
            while (rs.next()) {
                values.add(rs.getInt(1) + "|" + rs.getString(2));
            }
        }
        return values;
    }

    private static Instant instantOf(String idAndMillis) {
        return Instant.ofEpochMilli(Long.parseLong(idAndMillis.substring(idAndMillis.indexOf('|') + 1)));
    }

    private static void assertAllWithin(List<String> idAndMillis, RelativeWindow.Resolved window, String what) {
        assertFalse(idAndMillis.isEmpty(), what + " should have rows");
        for (String value : idAndMillis) {
            Instant instant = instantOf(value);
            assertFalse(instant.isBefore(window.start()), what + " " + instant + " is before " + window.start());
            assertTrue(instant.isBefore(window.end()), what + " " + instant + " is not before " + window.end());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // the motivating case
    // ---------------------------------------------------------------------------------------------

    @Test
    void aWindowRelativeToThePinnedAnchorSatisfiesRangeChecksAndAPartitionBound() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("public");
             Connection connection = dataSource.getConnection()) {
            fillSequential(new DatabaseFiller.Builder(connection, configuration(1)).asOf(AS_OF));

            assertEquals(ROWS, fixture.count("orders"));
            assertEquals(ROWS, fixture.count("order_notes"));
            assertEquals(ROWS, fixture.count("ledger_2026_q1"));

            // the CHECKs and the partition bound were enforced by the server on every insert; also look at what landed
            RelativeWindow.Resolved quarter = LAST_90_DAYS.resolve(AS_OF);
            assertEquals(Q1_START, quarter.start());
            assertAllWithin(epochMillis(connection, "placed_at", "orders"), quarter, "orders.placed_at");
            assertAllWithin(epochMillis(connection, "booked_at", "ledger_2026_q1"), quarter, "ledger_2026_q1.booked_at");
            assertAllWithin(epochMillis(connection, "noted_at", "order_notes"), LAST_10_DAYS.resolve(AS_OF), "order_notes.noted_at");

            Set<LocalDate> dueDates = new HashSet<>();
            for (String value : days(connection, "due_on", "orders")) {
                dueDates.add(LocalDate.parse(value.substring(value.indexOf('|') + 1)));
            }
            assertTrue(dueDates.stream().allMatch(d -> !d.isBefore(quarter.startDate()) && d.isBefore(quarter.endDate())));
            assertTrue(dueDates.size() > 60, "due dates should spread across the quarter, got " + dueDates.size());

            fixture.assertForeignKeysEnforced("public", 1);
        }
    }

    @Test
    void thePerColumnWindowBeatsTheCheckConstraintThatWouldOtherwiseShapeTheColumn() throws SQLException {
        DatabaseConfiguration tenDays = new DatabaseConfiguration.Builder(64, ROWS, new PostgresSupport())
                .seed(SEED)
                .tableConfigurations(Set.of(new TableConfiguration("orders", ROWS, Set.of(
                        timestampWithin("placed_at", LAST_10_DAYS), dateWithin("due_on", LAST_10_DAYS)))))
                .build();

        try (HikariDataSource dataSource = fixture.dataSource("public");
             Connection connection = dataSource.getConnection()) {
            fillSequential(new DatabaseFiller.Builder(connection, tenDays).asOf(AS_OF)
                    .includeTables("orders"));

            // the CHECK admits the whole quarter; only the configured window explains a ten-day spread
            assertAllWithin(epochMillis(connection, "placed_at", "orders"), LAST_10_DAYS.resolve(AS_OF), "orders.placed_at");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // reproducibility
    // ---------------------------------------------------------------------------------------------

    @Test
    void sameSeedAndAnchorReproduceTheRowsAndAnotherAnchorMovesThem() throws SQLException {
        List<String> firstStamps;
        List<String> firstDays;
        try (HikariDataSource dataSource = fixture.dataSource("public");
             Connection connection = dataSource.getConnection()) {

            fillSequential(events(connection, AS_OF));
            firstStamps = epochMillis(connection, "happened_at", "events");
            firstDays = days(connection, "happened_on", "events");
            assertEquals(2_000, firstStamps.size());

            fixture.reset("public");
            fillSequential(events(connection, AS_OF));
            assertEquals(firstStamps, epochMillis(connection, "happened_at", "events"), "same seed and asOf");
            assertEquals(firstDays, days(connection, "happened_on", "events"), "same seed and asOf");

            // 30 days later: the window moves, every draw moves by exactly the shift
            Instant later = AS_OF.plusSeconds(86_400L * 30);
            fixture.reset("public");
            fillSequential(events(connection, later));
            List<String> shiftedStamps = epochMillis(connection, "happened_at", "events");
            assertNotEquals(firstStamps, shiftedStamps);
            assertAllWithin(shiftedStamps, LAST_90_DAYS.resolve(later), "shifted events.happened_at");
            for (int i = 0; i < firstStamps.size(); i++) {
                assertEquals(instantOf(firstStamps.get(i)).plusSeconds(86_400L * 30), instantOf(shiftedStamps.get(i)));
            }
            assertNotEquals(firstDays, days(connection, "happened_on", "events"));
        }
    }

    private static DatabaseFiller.Builder events(Connection connection, Instant asOf) {
        return new DatabaseFiller.Builder(connection, configuration(1)).asOf(asOf).includeTables("events", "audit");
    }

    // ---------------------------------------------------------------------------------------------
    // one anchor across tables, threads and partitions
    // ---------------------------------------------------------------------------------------------

    /** A clock that says a different day every time it is read, and counts the reads. */
    private static final class TickingClock extends Clock {
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.parse("2026-04-01T12:00:00Z").plusSeconds(86_400L * reads.getAndIncrement());
        }
    }

    @Test
    void aParallelPartitionedFillReadsTheClockOnceAndEveryWorkerUsesThatAnchor() throws SQLException {
        TickingClock clock = new TickingClock();
        Instant resolved;
        List<String> parallelStamps;
        List<String> parallelAudit;
        List<String> parallelDays;

        try (HikariDataSource dataSource = fixture.dataSource("public")) {
            DatabaseFiller filler = new DatabaseFiller.Builder(dataSource, configuration(4))
                    .threads(4).includeTables("events", "audit").clock(clock).build();
            filler.fill();

            // read once for the whole fill, however many tables, partitions and workers it had
            assertEquals(1, clock.reads.get());
            resolved = filler.asOf().orElseThrow();
            assertEquals(Instant.parse("2026-04-01T00:00:00Z"), resolved);

            try (Connection connection = dataSource.getConnection()) {
                parallelStamps = epochMillis(connection, "happened_at", "events");
                parallelAudit = epochMillis(connection, "audited_at", "audit");
                parallelDays = days(connection, "happened_on", "events");
            }
            RelativeWindow.Resolved window = LAST_90_DAYS.resolve(resolved);
            assertEquals(2_000, parallelStamps.size());
            assertAllWithin(parallelStamps, window, "events.happened_at");
            assertAllWithin(parallelAudit, window, "audit.audited_at");

            // and the parallel, partitioned result is what a sequential fill with that anchor writes
            fixture.reset("public");
            try (Connection connection = dataSource.getConnection()) {
                fillSequential(events(connection, resolved));
                assertEquals(parallelStamps, epochMillis(connection, "happened_at", "events"));
                assertEquals(parallelAudit, epochMillis(connection, "audited_at", "audit"));
                assertEquals(parallelDays, days(connection, "happened_on", "events"));
            }
        }
    }

    @Test
    void aParallelFillWithAPinnedAnchorSatisfiesTheChecksToo() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("public")) {
            new DatabaseFiller.Builder(dataSource, configuration(4)).threads(4).asOf(AS_OF).build().fill();

            assertEquals(ROWS, fixture.count("orders"));
            assertEquals(ROWS, fixture.count("order_notes"));
            assertEquals(ROWS, fixture.count("ledger_2026_q1"));
            assertEquals(2_000, fixture.count("events"));
        }
    }
}
