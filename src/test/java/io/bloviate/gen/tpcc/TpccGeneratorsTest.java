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

package io.bloviate.gen.tpcc;

import io.bloviate.util.SeededRandomUtils;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpccGeneratorsTest {

    @Test
    void nonUniformRandomStaysWithinInclusiveBounds() {
        SeededRandomUtils randomUtils = new SeededRandomUtils(new Random(42));
        for (int i = 0; i < 5000; i++) {
            int value = TPCCUtils.nonUniformRandom(255, 157, 0, 999, randomUtils);
            assertTrue(value >= 0 && value <= 999, "out of bounds: " + value);
        }
    }

    @Test
    void nonUniformRandomIsDeterministicForAFixedSeed() {
        int first = TPCCUtils.nonUniformRandom(255, 157, 0, 999, new SeededRandomUtils(new Random(7)));
        int second = TPCCUtils.nonUniformRandom(255, 157, 0, 999, new SeededRandomUtils(new Random(7)));
        assertEquals(first, second);
    }

    @Test
    void creditIsAlwaysGoodOrBadAndBothOccur() {
        CreditGenerator generator = new CreditGenerator.Builder(new Random(1)).build();
        boolean sawGood = false;
        boolean sawBad = false;
        for (int i = 0; i < 2000; i++) {
            String value = generator.generate();
            assertTrue(CreditGenerator.GOOD_CREDIT.equals(value) || CreditGenerator.BAD_CREDIT.equals(value), "unexpected credit: " + value);
            sawGood |= CreditGenerator.GOOD_CREDIT.equals(value);
            sawBad |= CreditGenerator.BAD_CREDIT.equals(value);
        }
        assertTrue(sawGood && sawBad, "expected both GC and BC over many samples");
    }

    @Test
    void zipCodeIsFourDigitsFollowedBy11111() {
        ZipCodeGenerator generator = new ZipCodeGenerator.Builder(new Random(1)).build();
        for (int i = 0; i < 2000; i++) {
            String value = generator.generate();
            assertEquals(9, value.length());
            assertTrue(value.matches("\\d{4}11111"), "unexpected zip: " + value);
        }
    }

    @Test
    void dataColumnHasValidLengthAndSometimesContainsOriginal() {
        DataColumnGenerator generator = new DataColumnGenerator.Builder(new Random(1)).build();
        boolean sawOriginal = false;
        for (int i = 0; i < 5000; i++) {
            String value = generator.generate();
            assertTrue(value.length() >= 26 && value.length() <= 50, "unexpected length: " + value.length());
            sawOriginal |= value.contains(DataColumnGenerator.ORIGINAL);
        }
        assertTrue(sawOriginal, "expected some values to contain ORIGINAL");
    }

    @Test
    void customerLastNameIsThreeSyllables() {
        CustomerLastNameGenerator generator = new CustomerLastNameGenerator.Builder(new Random(1)).build();
        for (int i = 0; i < 2000; i++) {
            String value = generator.generate();
            // three syllables, each 3..5 uppercase letters -> length 9..15
            assertTrue(value.length() >= 9 && value.length() <= 15, "unexpected length: " + value);
            assertTrue(value.chars().allMatch(Character::isUpperCase), "expected upper-case letters: " + value);
        }
    }

    @Test
    void customerLastNameEnumeratesFirstNamesPerDistrictThenUsesNurand() {
        int groupSize = 10;
        int enumerated = 4;
        CustomerLastNameGenerator generator = new CustomerLastNameGenerator.Builder(new Random(1))
                .groupSize(groupSize).enumeratedCount(enumerated).build();

        // walk two full districts; the enumeration must restart at each district boundary
        for (int district = 0; district < 2; district++) {
            for (int position = 0; position < enumerated; position++) {
                assertEquals(CustomerLastNameGenerator.lastName(position), generator.generate(),
                        "enumerated name mismatch at district " + district + " position " + position);
            }
            for (int position = enumerated; position < groupSize; position++) {
                String value = generator.generate(); // NURand path -> still a valid three-syllable name
                assertTrue(value.length() >= 9 && value.length() <= 15, "unexpected length: " + value);
            }
        }
    }

    @Test
    void customerLastNameRejectsEnumeratedCountAboveSpecMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new CustomerLastNameGenerator.Builder(new Random(1))
                .groupSize(2000).enumeratedCount(CustomerLastNameGenerator.MAX_ENUMERATED + 1).build());
    }
}
