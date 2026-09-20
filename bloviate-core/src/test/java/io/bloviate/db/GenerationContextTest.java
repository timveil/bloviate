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

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anchor's resolution rules. Nothing here reads the wall clock: the unpinned default is exercised
 * through the package-private clock seam, so the assertions cannot flake around midnight.
 */
class GenerationContextTest {

    private static Clock fixed(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void aPinnedAnchorIsUsedAsIsWithoutTruncation() {
        Instant asOf = Instant.parse("2026-03-15T13:45:12.345Z");
        GenerationContext context = GenerationContext.pinned(asOf);
        assertEquals(asOf, context.asOf());
        assertEquals(asOf, context.peekAsOf());
        assertTrue(context.isPinned());
    }

    @Test
    void anUnpinnedAnchorIsTheStartOfTheCurrentUtcDay() {
        GenerationContext context = GenerationContext.unpinned(fixed("2026-03-15T13:45:12.345Z"));
        assertEquals(Instant.parse("2026-03-15T00:00:00Z"), context.asOf());
        assertFalse(context.isPinned());
    }

    @Test
    void theUnpinnedDayFollowsUtcNotTheJvmZoneAtTheEdgesOfTheDay() {
        assertEquals(Instant.parse("2026-03-15T00:00:00Z"),
                GenerationContext.unpinned(fixed("2026-03-15T00:00:00Z")).peekAsOf());
        assertEquals(Instant.parse("2026-03-15T00:00:00Z"),
                GenerationContext.unpinned(fixed("2026-03-15T23:59:59.999Z")).peekAsOf());
        assertEquals(Instant.parse("2026-03-16T00:00:00Z"),
                GenerationContext.unpinned(fixed("2026-03-16T00:00:00.001Z")).peekAsOf());
    }

    @Test
    void anUnpinnedAnchorIsResolvedOnceAndKeepsItsValueAsTheClockMoves() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-15T23:59:59Z"));
        GenerationContext context = GenerationContext.unpinned(clock);
        Instant first = context.asOf();
        clock.set(Instant.parse("2026-03-16T00:00:01Z"));
        assertEquals(first, context.asOf());
        assertEquals(first, context.peekAsOf());
        assertEquals(Instant.parse("2026-03-16T00:00:00Z"), GenerationContext.unpinned(clock).asOf());
    }

    @Test
    void theSystemClockDefaultIsTruncatedToTheDayAndNotInTheFuture() {
        Instant before = Instant.now();
        Instant asOf = GenerationContext.unpinned().peekAsOf();
        Instant after = Instant.now();
        assertEquals(asOf, asOf.truncatedTo(ChronoUnit.DAYS));
        assertFalse(asOf.isAfter(after));
        // within one day of "now" (which may itself have rolled over the day boundary since `before`)
        assertTrue(asOf.isAfter(before.minus(1, ChronoUnit.DAYS).minusSeconds(1)));
    }

    @Test
    void anAnchorIsRequired() {
        assertThrows(NullPointerException.class, () -> GenerationContext.pinned(null));
    }

    @Test
    void toStringNamesTheAnchorAndItsOrigin() {
        assertTrue(GenerationContext.pinned(Instant.parse("2026-01-01T00:00:00Z")).toString().contains("pinned"));
        assertTrue(GenerationContext.unpinned(fixed("2026-01-01T05:00:00Z")).toString().contains("from clock"));
    }

    /** A clock a test can move. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
