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

package io.bloviate.gen;

import io.bloviate.db.ColumnConfiguration;
import io.bloviate.db.ColumnGeneratorFactory;
import io.bloviate.db.Distributions;
import io.bloviate.db.GenerationContext;
import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The temporal generators honour a {@link RelativeWindow}: every value lies in {@code [start, end)} of the
 * window resolved for the anchor, the window moves with the anchor, and a fixed anchor gives fixed output.
 */
class RelativeWindowGeneratorsTest {

    private static final int ROWS = 20_000;
    private static final Instant AS_OF = Instant.parse("2026-04-01T00:00:00Z");
    private static final RelativeWindow LAST_90_DAYS = RelativeWindow.withinLast("90d");

    private static RelativeWindow.Resolved window(Instant asOf) {
        return LAST_90_DAYS.resolve(asOf);
    }

    private static RandomGenerator random() {
        return RandomGenerators.create(42L);
    }

    private static void assertInside(Instant value, RelativeWindow.Resolved window) {
        assertFalse(value.isBefore(window.start()), value + " is before " + window.start());
        assertTrue(value.isBefore(window.end()), value + " is not before " + window.end());
    }

    @Test
    void sqlTimestampStaysInsideTheWindow() {
        RelativeWindow.Resolved window = window(AS_OF);
        SqlTimestampGenerator generator = new SqlTimestampGenerator.Builder(random()).window(window).build();
        for (int i = 0; i < ROWS; i++) {
            assertInside(generator.generate().toInstant(), window);
        }
    }

    @Test
    void sqlDateDrawsWholeDatesInsideTheWindow() {
        // [2026-01-01, 2026-04-01): 90 dates
        RelativeWindow.Resolved window = window(AS_OF);
        SqlDateGenerator generator = new SqlDateGenerator.Builder(random()).window(window).build();
        Set<LocalDate> seen = new HashSet<>();
        for (int i = 0; i < ROWS; i++) {
            LocalDate date = generator.generate().toLocalDate();
            assertFalse(date.isBefore(window.startDate()), date.toString());
            assertTrue(date.isBefore(window.endDate()), date.toString());
            seen.add(date);
        }
        assertEquals(90, seen.size());
    }

    @Test
    void sqlDateRoundsARaggedWindowToWholeDatesAndRejectsOneWithNone() {
        RelativeWindow.Resolved ragged = new RelativeWindow.Resolved(
                Instant.parse("2026-01-01T06:00:00Z"), Instant.parse("2026-01-04T06:00:00Z"));
        SqlDateGenerator generator = new SqlDateGenerator.Builder(random()).window(ragged).build();
        Set<LocalDate> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(generator.generate().toLocalDate());
        }
        // midnights of the 2nd, 3rd and 4th are inside the window; the 1st's midnight is before its start
        assertEquals(Set.of(LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-03"), LocalDate.parse("2026-01-04")), seen);

        RelativeWindow.Resolved inADay = new RelativeWindow.Resolved(
                Instant.parse("2026-01-01T06:00:00Z"), Instant.parse("2026-01-01T18:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> new SqlDateGenerator.Builder(random()).window(inADay));
    }

    @Test
    void sqlDateStartAndEndAfterAWindowReturnToInstantBounds() {
        SqlDateGenerator generator = new SqlDateGenerator.Builder(random()).window(window(AS_OF))
                .start(java.sql.Date.valueOf("2020-01-01")).end(java.sql.Date.valueOf("2020-01-11")).build();
        for (int i = 0; i < 500; i++) {
            LocalDate date = generator.generate().toLocalDate();
            assertTrue(date.getYear() == 2020 && date.getMonthValue() == 1, date.toString());
        }
    }

    @Test
    void utilDateStaysInsideTheWindow() {
        RelativeWindow.Resolved window = window(AS_OF);
        DateGenerator generator = new DateGenerator.Builder(random()).window(window).build();
        for (int i = 0; i < ROWS; i++) {
            assertInside(generator.generate().toInstant(), window);
        }
    }

    @Test
    void instantStaysInsideTheWindow() {
        RelativeWindow.Resolved window = window(AS_OF);
        InstantGenerator generator = new InstantGenerator.Builder(random()).window(window).build();
        for (int i = 0; i < ROWS; i++) {
            assertInside(generator.generate(), window);
        }
    }

    @Test
    void skewedTimestampStaysInsideTheWindowAndLeansTowardItsEnd() {
        RelativeWindow.Resolved window = window(AS_OF);
        SkewedTimestampGenerator generator = new SkewedTimestampGenerator.Builder(random()).window(window).skew(4.0).build();
        Instant midpoint = window.start().plusMillis((window.end().toEpochMilli() - window.start().toEpochMilli()) / 2);
        int late = 0;
        for (int i = 0; i < ROWS; i++) {
            Instant value = generator.generate().toInstant();
            assertInside(value, window);
            if (value.isAfter(midpoint)) {
                late++;
            }
        }
        assertTrue(late > ROWS * 0.6, "a skewed window should favour its later half, got " + late);
    }

    @Test
    void skewedTimestampNeverReachesTheExclusiveEndOfAOneMillisecondWindow() {
        RelativeWindow.Resolved tiny = new RelativeWindow.Resolved(AS_OF, AS_OF.plusMillis(1));
        SkewedTimestampGenerator generator = new SkewedTimestampGenerator.Builder(random()).window(tiny).build();
        for (int i = 0; i < 1_000; i++) {
            assertEquals(AS_OF, generator.generate().toInstant());
        }
    }

    @Test
    void truncatedDateGeneratesPeriodStartsInsideTheWindow() {
        // [2025-10-01, 2026-04-01): six month starts
        RelativeWindow.Resolved window = RelativeWindow.withinLast("6M").resolve(AS_OF);
        TruncatedDateGenerator generator = new TruncatedDateGenerator.Builder(random()).window(window).build();
        List<LocalDate> seen = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            LocalDate value = generator.generate();
            assertEquals(1, value.getDayOfMonth());
            assertFalse(value.isBefore(LocalDate.parse("2025-10-01")), value.toString());
            assertTrue(value.isBefore(LocalDate.parse("2026-04-01")), value.toString());
            if (!seen.contains(value)) {
                seen.add(value);
            }
        }
        assertEquals(6, seen.size());
    }

    @Test
    void truncatedDateOnlyGeneratesAPeriodStartWhoseMidnightIsInsideAWindowWithATimeOfDay() {
        // [2026-01-01T06:00, 2026-04-01T06:00): 2026-01-01 midnight is before the start, 2026-04-01 midnight is inside
        RelativeWindow.Resolved window = new RelativeWindow.Resolved(
                Instant.parse("2026-01-01T06:00:00Z"), Instant.parse("2026-04-01T06:00:00Z"));
        TruncatedDateGenerator generator = new TruncatedDateGenerator.Builder(random()).window(window).build();
        List<LocalDate> seen = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            LocalDate value = generator.generate();
            if (!seen.contains(value)) {
                seen.add(value);
            }
        }
        assertTrue(seen.contains(LocalDate.parse("2026-04-01")));
        assertFalse(seen.contains(LocalDate.parse("2026-01-01")));
        assertEquals(3, seen.size());
    }

    @Test
    void sameSeedAndAnchorGiveTheSameValuesAndAnotherAnchorShiftsThem() {
        List<Instant> first = draw(AS_OF, 42L);
        assertEquals(first, draw(AS_OF, 42L));
        assertNotEquals(first, draw(AS_OF, 43L));

        Instant later = AS_OF.plusSeconds(86_400L * 30);
        List<Instant> shifted = draw(later, 42L);
        assertNotEquals(first, shifted);
        // same draws, moved by the 30-day shift of the anchor: the window is the only thing that changed
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).plusSeconds(86_400L * 30), shifted.get(i));
        }
    }

    private static List<Instant> draw(Instant asOf, long seed) {
        InstantGenerator generator = new InstantGenerator.Builder(RandomGenerators.create(seed)).window(window(asOf)).build();
        List<Instant> values = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            values.add(generator.generate());
        }
        return values;
    }

    @Test
    void relativeColumnConfigurationResolvesTheWindowAgainstTheContextAnchor() {
        ColumnConfiguration configuration = ColumnConfiguration.relative("placed_at", LAST_90_DAYS,
                (random, window) -> new InstantGenerator.Builder(random).window(window).build());
        assertEquals("placed_at", configuration.columnName());

        for (Instant asOf : new Instant[]{AS_OF, AS_OF.plusSeconds(86_400L * 400)}) {
            InstantGenerator generator = (InstantGenerator) configuration.generatorFactory()
                    .create(random(), GenerationContext.pinned(asOf));
            RelativeWindow.Resolved window = window(asOf);
            for (int i = 0; i < 1_000; i++) {
                assertInside(generator.generate(), window);
            }
        }
    }

    @Test
    void recentTimestampsDistributionAcceptsARelativeWindow() {
        ColumnGeneratorFactory factory = Distributions.recentTimestamps(LAST_90_DAYS, 3.0);
        RelativeWindow.Resolved window = window(AS_OF);
        SkewedTimestampGenerator generator = (SkewedTimestampGenerator) factory.create(random(), GenerationContext.pinned(AS_OF));
        for (int i = 0; i < 5_000; i++) {
            assertInside(generator.generate().toInstant(), window);
        }
    }

    @Test
    void contextualFactoryUsedWithoutAContextFallsBackToTheCurrentUtcDay() {
        ColumnGeneratorFactory factory = ColumnGeneratorFactory.contextual((random, context) -> {
            assertFalse(context.isPinned());
            return new InstantGenerator.Builder(random).window(window(context.asOf())).build();
        });
        assertTrue(factory.create(random()) instanceof InstantGenerator);
    }

    @Test
    void anOrdinaryFactoryIgnoresTheContext() {
        ColumnGeneratorFactory factory = random -> new InstantGenerator.Builder(random).build();
        InstantGenerator withContext = (InstantGenerator) factory.create(RandomGenerators.create(5L), GenerationContext.pinned(AS_OF));
        InstantGenerator without = (InstantGenerator) factory.create(RandomGenerators.create(5L));
        for (int i = 0; i < 100; i++) {
            assertEquals(without.generate(), withContext.generate());
        }
    }
}
