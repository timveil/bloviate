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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeWindowTest {

    private static final Instant AS_OF = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    void withinLastReachesBackFromTheAnchor() {
        RelativeWindow.Resolved window = RelativeWindow.withinLast("90d").resolve(AS_OF);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), window.start());
        assertEquals(AS_OF, window.end());
    }

    @Test
    void withinLastAcceptsCalendarUnits() {
        RelativeWindow.Resolved window = RelativeWindow.withinLast("3M").resolve(AS_OF);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), window.start());
        assertEquals(AS_OF, window.end());

        RelativeWindow.Resolved year = RelativeWindow.withinLast("1y").resolve(Instant.parse("2024-02-29T00:00:00Z"));
        assertEquals(Instant.parse("2023-02-28T00:00:00Z"), year.start());
        assertEquals(Instant.parse("2024-02-29T00:00:00Z"), year.end());
    }

    @Test
    void betweenMeasuresBothBoundsFromTheAnchor() {
        RelativeWindow.Resolved window = RelativeWindow.between("-30d", "+7d").resolve(AS_OF);
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), window.start());
        assertEquals(Instant.parse("2026-04-08T00:00:00Z"), window.end());
    }

    @Test
    void betweenMayLieEntirelyInTheFutureOrThePast() {
        RelativeWindow.Resolved future = RelativeWindow.between("1d", "8d").resolve(AS_OF);
        assertEquals(Instant.parse("2026-04-02T00:00:00Z"), future.start());
        assertEquals(Instant.parse("2026-04-09T00:00:00Z"), future.end());

        RelativeWindow.Resolved past = RelativeWindow.between("-8d", "-1d").resolve(AS_OF);
        assertEquals(Instant.parse("2026-03-24T00:00:00Z"), past.start());
        assertEquals(Instant.parse("2026-03-31T00:00:00Z"), past.end());
    }

    @Test
    void betweenAcceptsMixedUnits() {
        RelativeWindow.Resolved window = RelativeWindow.between("-1y", "12h").resolve(AS_OF);
        assertEquals(Instant.parse("2025-04-01T00:00:00Z"), window.start());
        assertEquals(Instant.parse("2026-04-01T12:00:00Z"), window.end());
    }

    @Test
    void resolvingIsAPureFunctionOfTheAnchor() {
        RelativeWindow window = RelativeWindow.withinLast("90d");
        assertEquals(window.resolve(AS_OF), window.resolve(AS_OF));
        assertEquals(window.resolve(AS_OF).start().plusSeconds(86_400L * 30), window.resolve(AS_OF.plusSeconds(86_400L * 30)).start());
    }

    @Test
    void withinLastRejectsZeroAndNegativeSpans() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class, () -> RelativeWindow.withinLast("0d"));
        assertTrue(zero.getMessage().contains("empty window"), zero.getMessage());
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> RelativeWindow.withinLast("-30d"));
        assertTrue(negative.getMessage().contains("positive"), negative.getMessage());
        assertTrue(negative.getMessage().contains("between"), negative.getMessage());
    }

    @Test
    void withinLastRejectsAnInvalidSpan() {
        assertThrows(IllegalArgumentException.class, () -> RelativeWindow.withinLast("soon"));
        assertThrows(NullPointerException.class, () -> RelativeWindow.withinLast((String) null));
        assertThrows(NullPointerException.class, () -> RelativeWindow.withinLast((RelativeOffset) null));
    }

    @Test
    void betweenRejectsAnEndThatIsNotAfterTheStart() {
        IllegalArgumentException inverted = assertThrows(IllegalArgumentException.class, () -> RelativeWindow.between("+7d", "-30d"));
        assertTrue(inverted.getMessage().contains("must be after"), inverted.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RelativeWindow.between("5d", "5d"));
        assertThrows(IllegalArgumentException.class, () -> RelativeWindow.between("24h", "1d"));
        assertThrows(IllegalArgumentException.class, () -> RelativeWindow.between("-1w", "-8d"));
    }

    @Test
    void betweenRejectsAnInvalidOffset() {
        assertThrows(IllegalArgumentException.class, () -> RelativeWindow.between("-30d", "tomorrow"));
        assertThrows(IllegalArgumentException.class, () -> RelativeWindow.between("", "1d"));
        assertThrows(NullPointerException.class, () -> RelativeWindow.between((String) null, "1d"));
        assertThrows(NullPointerException.class, () -> new RelativeWindow(null, RelativeOffset.parse("1d")));
        assertThrows(NullPointerException.class, () -> new RelativeWindow(RelativeOffset.parse("-1d"), null));
    }

    @Test
    void aWindowMixingCalendarAndFixedUnitsIsJudgedAgainstTheAnchor() {
        // -30d is before -1M when the previous month is 28 days long, after it when it has 31: this
        // cannot be judged without an anchor, so building the window succeeds
        RelativeWindow window = RelativeWindow.between("-30d", "-1M");

        RelativeWindow.Resolved february = window.resolve(Instant.parse("2026-03-01T00:00:00Z"));
        assertEquals(Instant.parse("2026-01-30T00:00:00Z"), february.start());
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), february.end());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> window.resolve(Instant.parse("2026-03-31T00:00:00Z")));
        assertTrue(e.getMessage().contains("2026-03-31T00:00:00Z"), e.getMessage());
        assertTrue(e.getMessage().contains("empty"), e.getMessage());
    }

    @Test
    void resolvingOutsideTheInstantRangeIsAnIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> RelativeWindow.withinLast("999999999999y").resolve(AS_OF));
    }

    @Test
    void resolvedWindowRejectsAnEmptyOrInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> new RelativeWindow.Resolved(AS_OF, AS_OF));
        assertThrows(IllegalArgumentException.class, () -> new RelativeWindow.Resolved(AS_OF, AS_OF.minusSeconds(1)));
        assertThrows(NullPointerException.class, () -> new RelativeWindow.Resolved(null, AS_OF));
        assertThrows(NullPointerException.class, () -> new RelativeWindow.Resolved(AS_OF, null));
        assertThrows(NullPointerException.class, () -> RelativeWindow.withinLast("1d").resolve(null));
    }

    @Test
    void resolvedWindowExposesTimestampsAndWholeDates() {
        RelativeWindow.Resolved midnight = new RelativeWindow.Resolved(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"));
        assertEquals(midnight.start(), midnight.startTimestamp().toInstant());
        assertEquals(midnight.end(), midnight.endTimestamp().toInstant());
        assertEquals(LocalDate.parse("2026-01-01"), midnight.startDate());
        assertEquals(LocalDate.parse("2026-04-01"), midnight.endDate());

        // a bound with a time of day rounds up: the date's own midnight is before the start / before the end
        RelativeWindow.Resolved ragged = new RelativeWindow.Resolved(
                Instant.parse("2026-01-01T06:00:00Z"), Instant.parse("2026-04-01T06:00:00Z"));
        assertEquals(LocalDate.parse("2026-01-02"), ragged.startDate());
        assertEquals(LocalDate.parse("2026-04-02"), ragged.endDate());
    }

    @Test
    void toStringIsTheOffsetPair() {
        assertEquals("[-90d, 0d)", RelativeWindow.withinLast("90d").toString());
        assertEquals("[-30d, 7d)", RelativeWindow.between("-30d", "+7d").toString());
    }
}
