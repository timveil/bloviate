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

import io.bloviate.ext.GeneratorFactory;
import io.bloviate.ext.GeneratorRegistry;
import io.bloviate.ext.H2Support;
import io.bloviate.gen.InstantGenerator;
import io.bloviate.gen.RelativeWindow;
import io.bloviate.gen.SqlTimestampGenerator;
import io.bloviate.gen.TruncatedDateGenerator;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #616 on H2: a relative window is resolved against one {@code asOf} anchor per fill, so the same
 * seed and the same pinned anchor reproduce the same rows, a different anchor moves the window, and an
 * unpinned anchor is settled once, from an injected clock, when the fill starts.
 */
class RelativeDateFillTest {

    private static final int ROWS = 400;
    private static final long SEED = 42L;
    private static final Instant AS_OF = Instant.parse("2026-04-01T00:00:00Z");
    private static final RelativeWindow LAST_90_DAYS = RelativeWindow.withinLast("90d");

    // one database name for the whole class, emptied before each fill: a column's seed includes the catalog
    // name, so fills that are compared with each other must run against a database of the same name (the
    // test methods of a class run one after another, so nothing else uses it meanwhile)
    private static final String URL = "jdbc:h2:mem:relative_dates;DB_CLOSE_DELAY=-1";

    // TIMESTAMP WITH TIME ZONE reads back as the exact instant that was bound, whatever the JVM zone
    private static final String DDL = """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY,
                occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
                billed_on DATE NOT NULL,
                note VARCHAR(20))""";

    private record Row(int id, Instant occurredAt, LocalDate billedOn) {
    }

    private static ColumnConfiguration occurredAtWithin(RelativeWindow window) {
        return ColumnConfiguration.relative("occurred_at", window,
                (random, resolved) -> new SqlTimestampGenerator.Builder(random).window(resolved).build());
    }

    private static ColumnConfiguration billedOnWithin(RelativeWindow window) {
        return ColumnConfiguration.relative("billed_on", window,
                (random, resolved) -> new TruncatedDateGenerator.Builder(random).window(resolved).build());
    }

    private static DatabaseConfiguration configuration(ColumnConfiguration... columns) {
        return configuration(null, columns);
    }

    private static DatabaseConfiguration configuration(GeneratorRegistry registry, ColumnConfiguration... columns) {
        return new DatabaseConfiguration.Builder(64, ROWS, new H2Support())
                .seed(SEED)
                .generatorRegistry(registry)
                .tableConfigurations(Set.of(new TableConfiguration("events", ROWS, Set.of(columns))))
                .build();
    }

    private static Connection freshDatabase() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
            statement.execute(DDL);
        }
        return connection;
    }

    private static List<Row> read(Connection connection) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select id, occurred_at, billed_on from events order by id")) {
            while (rs.next()) {
                rows.add(new Row(rs.getInt(1),
                        rs.getObject(2, OffsetDateTime.class).toInstant(),
                        rs.getObject(3, LocalDate.class)));
            }
        }
        return rows;
    }

    /** Fills a fresh database with a pinned anchor and returns what was written. */
    private static List<Row> fillPinned(Instant asOf, DatabaseConfiguration configuration) throws SQLException {
        try (Connection connection = freshDatabase()) {
            new DatabaseFiller.Builder(connection, configuration).asOf(asOf).build().fill();
            return read(connection);
        }
    }

    private static void assertOccurredWithin(List<Row> rows, RelativeWindow.Resolved window) {
        assertEquals(ROWS, rows.size());
        for (Row row : rows) {
            assertFalse(row.occurredAt().isBefore(window.start()), row.occurredAt() + " < " + window.start());
            assertTrue(row.occurredAt().isBefore(window.end()), row.occurredAt() + " >= " + window.end());
        }
        // a window is populated across its length, not bunched at one end
        Instant first = rows.stream().map(Row::occurredAt).min(Instant::compareTo).orElseThrow();
        Instant last = rows.stream().map(Row::occurredAt).max(Instant::compareTo).orElseThrow();
        long span = window.end().toEpochMilli() - window.start().toEpochMilli();
        assertTrue(last.toEpochMilli() - first.toEpochMilli() > span * 0.9, "values should spread over the window");
    }

    @Test
    void samePinnedAnchorAndSeedReproduceTheRowsAndAnotherSeedDoesNot() throws SQLException {
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));

        List<Row> first = fillPinned(AS_OF, configuration);
        List<Row> second = fillPinned(AS_OF, configuration);

        assertEquals(first, second);
        assertNotEquals(first, fillPinned(AS_OF,
                new DatabaseConfiguration.Builder(64, ROWS, new H2Support()).seed(SEED + 1)
                        .tableConfigurations(Set.of(new TableConfiguration("events", ROWS,
                                Set.of(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS)))))
                        .build()));
    }

    @Test
    void everyValueIsInsideTheWindowForThePinnedAnchor() throws SQLException {
        List<Row> rows = fillPinned(AS_OF, configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS)));

        RelativeWindow.Resolved window = LAST_90_DAYS.resolve(AS_OF);
        assertOccurredWithin(rows, window);
        for (Row row : rows) {
            assertEquals(1, row.billedOn().getDayOfMonth());
            assertFalse(row.billedOn().isBefore(window.startDate()), row.billedOn().toString());
            assertTrue(row.billedOn().isBefore(window.endDate()), row.billedOn().toString());
        }
    }

    @Test
    void aDifferentAnchorMovesTheWindowAndTheValuesWithIt() throws SQLException {
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));
        Instant later = AS_OF.plusSeconds(86_400L * 200);

        List<Row> atFirst = fillPinned(AS_OF, configuration);
        List<Row> atLater = fillPinned(later, configuration);

        assertOccurredWithin(atFirst, LAST_90_DAYS.resolve(AS_OF));
        assertOccurredWithin(atLater, LAST_90_DAYS.resolve(later));
        assertNotEquals(atFirst, atLater);
        // same seed, same window length: every draw moves by exactly the shift of the anchor
        for (int i = 0; i < ROWS; i++) {
            assertEquals(atFirst.get(i).occurredAt().plusSeconds(86_400L * 200), atLater.get(i).occurredAt());
        }
    }

    @Test
    void aWindowInTheFutureIsHonouredToo() throws SQLException {
        RelativeWindow upcoming = RelativeWindow.between("+1d", "+30d");
        List<Row> rows = fillPinned(AS_OF, configuration(occurredAtWithin(upcoming), billedOnWithin(LAST_90_DAYS)));
        assertOccurredWithin(rows, upcoming.resolve(AS_OF));
    }

    // ---------------------------------------------------------------------------------------------
    // the anchor when none is pinned
    // ---------------------------------------------------------------------------------------------

    @Test
    void anUnpinnedAnchorIsTheUtcDayOfTheClockAtFillStartAndIsExposed() throws SQLException {
        Clock clock = Clock.fixed(Instant.parse("2026-06-10T17:25:09Z"), ZoneOffset.UTC);
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));

        try (Connection connection = freshDatabase()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration).clock(clock).build();
            assertEquals(Optional.empty(), filler.asOf(), "nothing is resolved before the fill starts");

            filler.fill();

            Instant expected = Instant.parse("2026-06-10T00:00:00Z");
            assertEquals(Optional.of(expected), filler.asOf());
            assertOccurredWithin(read(connection), LAST_90_DAYS.resolve(expected));
        }
    }

    @Test
    void twoUnpinnedFillsOnTheSameDayAgreeAndMatchAFillPinnedToTheLoggedInstant() throws SQLException {
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));
        Clock morning = Clock.fixed(Instant.parse("2026-06-10T00:00:01Z"), ZoneOffset.UTC);
        Clock evening = Clock.fixed(Instant.parse("2026-06-10T23:59:59Z"), ZoneOffset.UTC);

        List<Row> first;
        Instant resolved;
        try (Connection connection = freshDatabase()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration).clock(morning).build();
            filler.fill();
            first = read(connection);
            resolved = filler.asOf().orElseThrow();
        }
        List<Row> second;
        try (Connection connection = freshDatabase()) {
            new DatabaseFiller.Builder(connection, configuration).clock(evening).build().fill();
            second = read(connection);
        }

        assertEquals(first, second);
        // and pinning what an unpinned fill resolved reproduces it, which is how the INFO line is meant to be used
        assertEquals(first, fillPinned(resolved, configuration));
    }

    @Test
    void theNextDayGivesADifferentUnpinnedAnchorAndTheWindowFollows() throws SQLException {
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));
        Clock today = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);
        Clock tomorrow = Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

        List<Row> first;
        try (Connection connection = freshDatabase()) {
            new DatabaseFiller.Builder(connection, configuration).clock(today).build().fill();
            first = read(connection);
        }
        try (Connection connection = freshDatabase()) {
            new DatabaseFiller.Builder(connection, configuration).clock(tomorrow).build().fill();
            List<Row> second = read(connection);
            assertNotEquals(first, second);
            assertOccurredWithin(second, LAST_90_DAYS.resolve(Instant.parse("2026-06-11T00:00:00Z")));
        }
    }

    @Test
    void aPinnedAnchorIgnoresTheClock() throws SQLException {
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));
        Clock elsewhere = Clock.fixed(Instant.parse("2031-01-01T00:00:00Z"), ZoneOffset.UTC);
        try (Connection connection = freshDatabase()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration).asOf(AS_OF).clock(elsewhere).build();
            assertEquals(Optional.of(AS_OF), filler.asOf());
            filler.fill();
            assertEquals(Optional.of(AS_OF), filler.asOf());
            assertOccurredWithin(read(connection), LAST_90_DAYS.resolve(AS_OF));
        }
    }

    @Test
    void aReusedPinnedFillerKeepsItsAnchorAcrossFills() throws SQLException {
        DatabaseConfiguration configuration = configuration(occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS));
        try (Connection connection = freshDatabase()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration).asOf(AS_OF).build();
            filler.fill();
            try (Statement statement = connection.createStatement()) {
                statement.execute("delete from events");
            }
            filler.fill();
            assertOccurredWithin(read(connection), LAST_90_DAYS.resolve(AS_OF));
        }
    }

    @Test
    void aFillWithNoRelativeWindowIsUnaffectedByTheAnchor() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration.Builder(64, ROWS, new H2Support()).seed(SEED).build();
        List<Row> pinned = fillPinned(AS_OF, configuration);
        List<Row> other = fillPinned(AS_OF.plusSeconds(86_400L * 1_000), configuration);
        List<Row> unpinned;
        try (Connection connection = freshDatabase()) {
            new DatabaseFiller.Builder(connection, configuration).build().fill();
            unpinned = read(connection);
        }
        assertEquals(pinned, other);
        assertEquals(pinned, unpinned);
    }

    // ---------------------------------------------------------------------------------------------
    // registry rules and precedence
    // ---------------------------------------------------------------------------------------------

    @Test
    void aContextualRegistryRuleSeesTheFillAnchor() throws SQLException {
        RelativeWindow lastMonth = RelativeWindow.withinLast("30d");
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerColumnNamePattern("(?i)occurred_at", GeneratorFactory.contextual((column, random, context) ->
                        new SqlTimestampGenerator.Builder(random).window(lastMonth.resolve(context.asOf())).build()))
                .build();
        DatabaseConfiguration configuration = new DatabaseConfiguration.Builder(64, ROWS, new H2Support())
                .seed(SEED).generatorRegistry(registry).build();

        List<Row> rows = fillPinned(AS_OF, configuration);
        assertOccurredWithin(rows, lastMonth.resolve(AS_OF));
        assertEquals(rows, fillPinned(AS_OF, configuration));
    }

    @Test
    void aPerColumnRelativeConfigurationBeatsARegistryRule() throws SQLException {
        RelativeWindow registryWindow = RelativeWindow.between("-400d", "-300d");
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerColumnNamePattern("(?i)occurred_at", GeneratorFactory.contextual((column, random, context) ->
                        new SqlTimestampGenerator.Builder(random).window(registryWindow.resolve(context.asOf())).build()))
                .build();

        List<Row> rows = fillPinned(AS_OF, configuration(registry, occurredAtWithin(LAST_90_DAYS), billedOnWithin(LAST_90_DAYS)));

        assertOccurredWithin(rows, LAST_90_DAYS.resolve(AS_OF));
    }

    @Test
    void aPlainRegistryRuleStillWorksWithoutAContext() throws SQLException {
        Instant fixedStart = Instant.parse("2020-01-01T00:00:00Z");
        Instant fixedEnd = Instant.parse("2020-02-01T00:00:00Z");
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerColumnNamePattern("(?i)occurred_at", (column, random) ->
                        new SqlTimestampGenerator.Builder(random)
                                .start(Timestamp.from(fixedStart)).end(Timestamp.from(fixedEnd)).build())
                .build();

        List<Row> rows = fillPinned(AS_OF, new DatabaseConfiguration.Builder(64, ROWS, new H2Support())
                .seed(SEED).generatorRegistry(registry).build());

        for (Row row : rows) {
            assertFalse(row.occurredAt().isBefore(fixedStart));
            assertTrue(row.occurredAt().isBefore(fixedEnd));
        }
    }

    @Test
    void aWindowThatCannotBeResolvedFailsTheFillWithTheAnchorInTheMessage() throws SQLException {
        // 30 days is longer than one month whenever the previous month has 31 days, but not for 2026-03-01
        RelativeWindow fragile = RelativeWindow.between("-30d", "-1M");
        ColumnConfiguration column = ColumnConfiguration.relative("occurred_at", fragile,
                (random, resolved) -> new InstantGenerator.Builder(random).window(resolved).build());
        try (Connection connection = freshDatabase()) {
            DatabaseFiller filler = new DatabaseFiller.Builder(connection, configuration(column))
                    .asOf(Instant.parse("2026-03-31T00:00:00Z")).build();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, filler::fill);
            assertTrue(e.getMessage().contains("2026-03-31T00:00:00Z"), e.getMessage());
        }
    }
}
