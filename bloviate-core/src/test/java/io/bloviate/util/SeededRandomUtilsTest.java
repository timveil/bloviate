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

package io.bloviate.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SeededRandomUtils} — the pool fast paths, the general {@code [start, end)}
 * code-point path, and the bounded numeric helpers including their equal-bounds and validation edges.
 */
class SeededRandomUtilsTest {

    private static SeededRandomUtils utils(long seed) {
        return new SeededRandomUtils(RandomGenerators.create(seed));
    }

    @Test
    void poolFastPathsDrawOnlyFromTheirCharacterSet() {
        SeededRandomUtils u = utils(1L);
        assertTrue(u.random(200, true, false).matches("[A-Za-z]+"), "letters only");
        assertTrue(u.random(200, false, true).matches("[0-9]+"), "digits only");
        assertTrue(u.random(200, true, true).matches("[A-Za-z0-9]+"), "alphanumeric");
        assertTrue(u.randomAlphabetic(50).matches("[A-Za-z]+"));
        assertTrue(u.randomNumeric(50).matches("[0-9]+"));
    }

    @Test
    void zeroLengthAndNegativeLengthAreHandled() {
        SeededRandomUtils u = utils(2L);
        assertEquals("", u.random(0, true, true));
        assertEquals("", u.random(0, ' ', 'z' + 1, true, false));
        assertThrows(IllegalArgumentException.class, () -> u.random(-1, true, true));
        assertThrows(IllegalArgumentException.class, () -> u.random(-1, ' ', 'z' + 1, true, false));
    }

    @Test
    void noFlagsDelegatesToTheGeneralAnyCodePointPath() {
        // !letters && !numbers routes through random(count, 0, 0, false, false) which spans all code
        // points; count is the number of UTF-16 chars, so the result length is exactly count
        String s = utils(3L).random(64, false, false);
        assertEquals(64, s.length());
    }

    @Test
    void generalPathDefaultRangeFiltersLettersAndDigits() {
        SeededRandomUtils u = utils(4L);
        // start==end==0 with a flag selects the printable ' '..'z' window, then keeps only matches
        assertTrue(u.random(100, 0, 0, true, false).matches("[A-Za-z]+"), "letters kept from default window");
        assertTrue(u.random(100, 0, 0, false, true).matches("[0-9]+"), "digits kept from default window");
    }

    @Test
    void generalPathExplicitRangeAndSupplementaryCodePoints() {
        SeededRandomUtils u = utils(5L);
        // an explicit letter window keeps only letters
        assertTrue(u.random(80, 'a', 'z' + 1, true, false).matches("[a-z]+"));
        // a supplementary-plane window (chars that need a surrogate pair) exercises the charCount==2
        // path: count is in UTF-16 chars, so 40 chars == 20 supplementary code points
        String supplementary = u.random(40, 0x10000, 0x10010, false, false);
        assertEquals(40, supplementary.length());
        assertEquals(20, supplementary.codePointCount(0, supplementary.length()), "all code points are supplementary");
    }

    @Test
    void generalPathRejectsInvalidRanges() {
        SeededRandomUtils u = utils(6L);
        assertThrows(IllegalArgumentException.class, () -> u.random(5, 100, 50, false, false), "end <= start");
        assertThrows(IllegalArgumentException.class, () -> u.random(5, -1, 50, false, false), "negative start");
        // end above the max code point is clamped rather than rejected
        String clamped = u.random(20, 32, Character.MAX_CODE_POINT + 100, false, false);
        assertEquals(20, clamped.length());
    }

    @Test
    void boundedNumericHelpersStayInRange() {
        SeededRandomUtils u = utils(7L);
        for (int i = 0; i < 1_000; i++) {
            int n = u.nextInt(5, 10);
            assertTrue(n >= 5 && n < 10);
            double d = u.nextDouble(1.0, 2.0);
            assertTrue(d >= 1.0 && d < 2.0);
            float f = u.nextFloat(1.0f, 2.0f);
            assertTrue(f >= 1.0f && f < 2.0f);
            long l = u.nextLong(100L, 200L);
            assertTrue(l >= 100L && l < 200L);
        }
    }

    @Test
    void boundedNumericHelpersReturnStartWhenBoundsAreEqual() {
        SeededRandomUtils u = utils(8L);
        assertEquals(5, u.nextInt(5, 5));
        assertEquals(2.5, u.nextDouble(2.5, 2.5));
        assertEquals(2.5f, u.nextFloat(2.5f, 2.5f));
        assertEquals(42L, u.nextLong(42L, 42L));
    }

    @Test
    void boundedNumericHelpersRejectInvalidBounds() {
        SeededRandomUtils u = utils(9L);
        assertThrows(IllegalArgumentException.class, () -> u.nextInt(10, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextInt(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextDouble(10, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextDouble(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextFloat(10, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextFloat(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextLong(10, 5));
        assertThrows(IllegalArgumentException.class, () -> u.nextLong(-1, 5));
    }
}
