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

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupedPermutationGeneratorTest {

    private static GroupedPermutationGenerator generator(int groupSize, int start, long seed) {
        return new GroupedPermutationGenerator.Builder(new Random()).groupSize(groupSize).start(start).seed(seed).build();
    }

    @Test
    void eachGroupIsAPermutationOfTheRange() {
        int groupSize = 50;
        int start = 1;
        int groups = 40;
        GroupedPermutationGenerator generator = generator(groupSize, start, 12345);

        for (int g = 0; g < groups; g++) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < groupSize; i++) {
                int value = generator.generate();
                assertTrue(value >= start && value < start + groupSize, "out of range: " + value);
                assertTrue(seen.add(value), "duplicate within group " + g + ": " + value);
            }
            assertEquals(groupSize, seen.size(), "group " + g + " is not a full permutation");
        }
    }

    @Test
    void worksForNonPowerOfTwoAndOddSizes() {
        for (int groupSize : new int[]{1, 2, 3, 7, 13, 100, 999, 3000}) {
            GroupedPermutationGenerator generator = generator(groupSize, 0, 777);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < groupSize; i++) {
                seen.add(generator.generate());
            }
            assertEquals(groupSize, seen.size(), "size " + groupSize + " did not produce a permutation");
            assertTrue(seen.stream().allMatch(v -> v >= 0 && v < groupSize), "out of range for size " + groupSize);
        }
    }

    @Test
    void groupSizeOneAlwaysReturnsStart() {
        GroupedPermutationGenerator generator = generator(1, 5, 1);
        for (int i = 0; i < 100; i++) {
            assertEquals(5, generator.generate().intValue());
        }
    }

    @Test
    void isDeterministicForAFixedSeed() {
        GroupedPermutationGenerator a = generator(64, 1, 2024);
        GroupedPermutationGenerator b = generator(64, 1, 2024);
        for (int i = 0; i < 256; i++) {
            assertEquals(a.generate(), b.generate());
        }
    }

    @Test
    void actuallyShufflesAndDiffersAcrossGroups() {
        int groupSize = 100;
        GroupedPermutationGenerator generator = generator(groupSize, 0, 99);

        int[] first = new int[groupSize];
        int[] second = new int[groupSize];
        boolean firstIsIdentity = true;
        for (int i = 0; i < groupSize; i++) {
            first[i] = generator.generate();
            firstIsIdentity &= (first[i] == i);
        }
        for (int i = 0; i < groupSize; i++) {
            second[i] = generator.generate();
        }

        assertFalse(firstIsIdentity, "permutation should not be the identity for this seed/size");

        boolean differsAcrossGroups = false;
        for (int i = 0; i < groupSize; i++) {
            differsAcrossGroups |= (first[i] != second[i]);
        }
        assertTrue(differsAcrossGroups, "consecutive groups should have independent permutations");
    }

    @Test
    void rejectsInvalidGroupSize() {
        assertThrows(IllegalArgumentException.class, () -> generator(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> generator(-5, 0, 1));
    }
}
