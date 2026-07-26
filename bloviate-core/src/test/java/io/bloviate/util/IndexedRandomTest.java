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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link IndexedRandom} contract the fill engine depends on for O(1) partition seeks:
 * the draws after {@code position(k)} are a pure function of {@code (seed, k)}, independent of any
 * draws consumed before the reposition.
 */
class IndexedRandomTest {

    private static final long SEED = 42L;

    @Test
    void drawsAfterPositionAreIndependentOfHistory() {
        // reference: a fresh instance positioned straight at row 1000
        IndexedRandom reference = new IndexedRandom(SEED);
        reference.position(1000);
        long[] expected = {reference.nextLong(), reference.nextLong(), reference.nextLong()};

        // a heavily-used instance repositioned to the same row must replay the same draws,
        // regardless of how many draws earlier "rows" consumed
        IndexedRandom used = new IndexedRandom(SEED);
        for (int row = 0; row < 100; row++) {
            used.position(row);
            for (int draw = 0; draw <= row % 7; draw++) {
                used.nextLong();
            }
        }
        used.position(1000);
        assertEquals(expected[0], used.nextLong());
        assertEquals(expected[1], used.nextLong());
        assertEquals(expected[2], used.nextLong());
    }

    @Test
    void sameSeedAndPositionReplaysAcrossInstances() {
        IndexedRandom first = new IndexedRandom(SEED);
        IndexedRandom second = new IndexedRandom(SEED);
        for (long index : new long[]{0, 1, 7, 1_000_000L, Long.MAX_VALUE / 2}) {
            first.position(index);
            second.position(index);
            assertEquals(first.nextLong(), second.nextLong(), "index " + index);
            assertEquals(first.nextDouble(), second.nextDouble(), "index " + index);
            assertEquals(first.nextInt(1, 1000), second.nextInt(1, 1000), "index " + index);
        }
    }

    @Test
    void adjacentIndexesAndSeedsProduceDistinctStreams() {
        // covers the aliasing hazard the index mix exists for: column seeds are arithmetically
        // related (baseSeed * K + identity), so seed s+1 must not replay seed s's rows at an offset
        Set<Long> values = new HashSet<>();
        for (long index = 0; index < 1000; index++) {
            IndexedRandom sameSeed = new IndexedRandom(SEED);
            sameSeed.position(index);
            values.add(sameSeed.nextLong());
            IndexedRandom nextSeed = new IndexedRandom(SEED + 1);
            nextSeed.position(index);
            values.add(nextSeed.nextLong());
        }
        assertEquals(2000, values.size(), "first draws must be distinct across indexes and adjacent seeds");
    }

    @Test
    void constructorStateMatchesPositionZero() {
        IndexedRandom constructed = new IndexedRandom(SEED);
        IndexedRandom positioned = new IndexedRandom(SEED);
        positioned.position(0);
        assertEquals(constructed.nextLong(), positioned.nextLong());
    }

    @Test
    void nextGaussianHoldsNoCrossDrawState() {
        // the RandomGenerator default nextGaussian must not cache a second value across repositions:
        // positioning back to the same index must replay the identical gaussian
        IndexedRandom random = new IndexedRandom(SEED);
        random.position(5);
        double first = random.nextGaussian();
        random.position(5);
        assertEquals(first, random.nextGaussian());
        random.position(6);
        assertNotEquals(first, random.nextGaussian());
    }

    @Test
    void streamMatchesDocumentedSplitmixDerivation() {
        // draw j after position(k) is splitmix64(seed + splitmix64(k) + j * GOLDEN_GAMMA); pin it
        // so the derivation can never drift silently (it is part of the cross-version data contract)
        long gamma = 0x9E3779B97F4A7C15L;
        IndexedRandom random = new IndexedRandom(SEED);
        random.position(123);
        assertEquals(Mixers.splitmix64(SEED + Mixers.splitmix64(123) + gamma), random.nextLong());
        assertEquals(Mixers.splitmix64(SEED + Mixers.splitmix64(123) + 2 * gamma), random.nextLong());
    }

    @Test
    void negativePositionIsRejected() {
        IndexedRandom random = new IndexedRandom(SEED);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> random.position(-1))
                .getMessage().contains("-1"));
    }
}
