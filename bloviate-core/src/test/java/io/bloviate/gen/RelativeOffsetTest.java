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

import static io.bloviate.gen.RelativeOffset.Unit.DAYS;
import static io.bloviate.gen.RelativeOffset.Unit.HOURS;
import static io.bloviate.gen.RelativeOffset.Unit.MONTHS;
import static io.bloviate.gen.RelativeOffset.Unit.WEEKS;
import static io.bloviate.gen.RelativeOffset.Unit.YEARS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeOffsetTest {

    @Test
    void parsesEveryUnit() {
        assertEquals(new RelativeOffset(36, HOURS), RelativeOffset.parse("36h"));
        assertEquals(new RelativeOffset(90, DAYS), RelativeOffset.parse("90d"));
        assertEquals(new RelativeOffset(12, WEEKS), RelativeOffset.parse("12w"));
        assertEquals(new RelativeOffset(6, MONTHS), RelativeOffset.parse("6M"));
        assertEquals(new RelativeOffset(1, YEARS), RelativeOffset.parse("1y"));
    }

    @Test
    void parsesSignsAndIgnoresSurroundingWhitespace() {
        assertEquals(new RelativeOffset(-30, DAYS), RelativeOffset.parse("-30d"));
        assertEquals(new RelativeOffset(7, DAYS), RelativeOffset.parse("+7d"));
        assertEquals(new RelativeOffset(7, DAYS), RelativeOffset.parse("7d"));
        assertEquals(new RelativeOffset(0, DAYS), RelativeOffset.parse("-0d"));
        assertEquals(new RelativeOffset(-3, MONTHS), RelativeOffset.parse("  -3M \n"));
    }

    @Test
    void textFormRoundTrips() {
        for (String text : new String[]{"-30d", "7d", "0h", "-1y", "12w", "6M"}) {
            assertEquals(text, RelativeOffset.parse(text).toString());
        }
        assertEquals("7d", RelativeOffset.parse("+7d").toString());
    }

    @Test
    void rejectsMalformedText() {
        for (String text : new String[]{"", "   ", "d", "90", "90 d", "9 0d", "--5d", "+-5d", "5dd", "1.5d", "5d5h", "1d 2h", "ninety"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RelativeOffset.parse(text), text);
            assertTrue(e.getMessage().contains("offset"), e.getMessage());
        }
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> RelativeOffset.parse(null));
    }

    @Test
    void rejectsUnknownOrMiscasedUnitsNamingTheValidOnes() {
        for (String text : new String[]{"5m", "5s", "5D", "5H", "5W", "5Y", "5q"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RelativeOffset.parse(text), text);
            assertTrue(e.getMessage().contains("h (hours), d (days), w (weeks), M (months), y (years)"), e.getMessage());
            assertTrue(e.getMessage().contains("case-sensitive"), e.getMessage());
        }
    }

    @Test
    void rejectsAnAmountTooLargeForALong() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RelativeOffset.parse("99999999999999999999d"));
        assertTrue(e.getMessage().contains("too large"), e.getMessage());
    }

    @Test
    void hoursDaysAndWeeksAreExactDurations() {
        Instant anchor = Instant.parse("2026-03-08T00:00:00Z");
        assertEquals(Instant.parse("2026-03-09T12:00:00Z"), RelativeOffset.parse("36h").addTo(anchor));
        assertEquals(Instant.parse("2025-12-08T00:00:00Z"), RelativeOffset.parse("-90d").addTo(anchor));
        assertEquals(Instant.parse("2026-05-31T00:00:00Z"), RelativeOffset.parse("12w").addTo(anchor));
        assertEquals(anchor, RelativeOffset.parse("0d").addTo(anchor));
    }

    @Test
    void monthsAndYearsFollowTheCalendar() {
        Instant anchor = Instant.parse("2026-03-15T10:30:00Z");
        assertEquals(Instant.parse("2025-09-15T10:30:00Z"), RelativeOffset.parse("-6M").addTo(anchor));
        assertEquals(Instant.parse("2026-09-15T10:30:00Z"), RelativeOffset.parse("6M").addTo(anchor));
        assertEquals(Instant.parse("2025-03-15T10:30:00Z"), RelativeOffset.parse("-1y").addTo(anchor));
        assertEquals(Instant.parse("2027-03-15T10:30:00Z"), RelativeOffset.parse("+1y").addTo(anchor));
        // 12 months is a year, unlike 360 days
        assertEquals(RelativeOffset.parse("1y").addTo(anchor), RelativeOffset.parse("12M").addTo(anchor));
    }

    @Test
    void monthEndIsClampedToTheTargetMonth() {
        assertEquals(Instant.parse("2026-02-28T00:00:00Z"),
                RelativeOffset.parse("-1M").addTo(Instant.parse("2026-03-31T00:00:00Z")));
        assertEquals(Instant.parse("2024-02-29T00:00:00Z"),
                RelativeOffset.parse("-1M").addTo(Instant.parse("2024-03-31T00:00:00Z")));
        assertEquals(Instant.parse("2026-04-30T00:00:00Z"),
                RelativeOffset.parse("+1M").addTo(Instant.parse("2026-03-31T00:00:00Z")));
        assertEquals(Instant.parse("2025-11-30T00:00:00Z"),
                RelativeOffset.parse("3M").addTo(Instant.parse("2025-08-31T00:00:00Z")));
    }

    @Test
    void leapDayIsClampedWhenAYearLandsOnANonLeapYear() {
        Instant leapDay = Instant.parse("2024-02-29T06:00:00Z");
        assertEquals(Instant.parse("2025-02-28T06:00:00Z"), RelativeOffset.parse("1y").addTo(leapDay));
        assertEquals(Instant.parse("2023-02-28T06:00:00Z"), RelativeOffset.parse("-1y").addTo(leapDay));
        assertEquals(Instant.parse("2028-02-29T06:00:00Z"), RelativeOffset.parse("4y").addTo(leapDay));
        // 90 days across a leap day is still exactly 90 days
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                RelativeOffset.parse("-60d").addTo(Instant.parse("2024-03-01T00:00:00Z")));
    }

    @Test
    void daylightSavingNeverBendsADay() {
        // 2026-03-08 is the US spring-forward date; in UTC that day still has 24 hours
        Instant anchor = Instant.parse("2026-03-08T00:00:00Z");
        assertEquals(Instant.parse("2026-03-09T00:00:00Z"), RelativeOffset.parse("1d").addTo(anchor));
        assertEquals(Instant.parse("2026-03-09T00:00:00Z"), RelativeOffset.parse("24h").addTo(anchor));
    }

    @Test
    void anOffsetOutsideTheInstantRangeIsAnIllegalArgument() {
        Instant anchor = Instant.parse("2026-03-08T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> RelativeOffset.parse("999999999999y").addTo(anchor));
        assertThrows(IllegalArgumentException.class, () -> RelativeOffset.parse("9223372036854775807d").addTo(anchor));
        assertThrows(IllegalArgumentException.class, () -> RelativeOffset.parse("999999999999999M").addTo(anchor));
    }

    @Test
    void negateFlipsTheDirection() {
        assertEquals(new RelativeOffset(-5, DAYS), new RelativeOffset(5, DAYS).negate());
        assertEquals(new RelativeOffset(5, MONTHS), new RelativeOffset(-5, MONTHS).negate());
    }

    @Test
    void symbolsAreTheDocumentedLetters() {
        assertEquals("hdwMy", "" + HOURS.symbol() + DAYS.symbol() + WEEKS.symbol() + MONTHS.symbol() + YEARS.symbol());
    }

    @Test
    void unitIsRequired() {
        assertThrows(NullPointerException.class, () -> new RelativeOffset(1, null));
    }

    @Test
    void fixedLengthOnlyForHoursDaysAndWeeks() {
        assertTrue(RelativeOffset.parse("1h").isFixedLength());
        assertTrue(RelativeOffset.parse("1d").isFixedLength());
        assertTrue(RelativeOffset.parse("1w").isFixedLength());
        assertFalse(RelativeOffset.parse("1M").isFixedLength());
        assertFalse(RelativeOffset.parse("1y").isFixedLength());
    }
}
