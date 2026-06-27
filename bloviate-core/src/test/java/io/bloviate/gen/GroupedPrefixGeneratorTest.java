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

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupedPrefixGeneratorTest {

    private static GroupedPrefixGenerator<Integer> generator(int groupSize, int prefixSize, int value) {
        return new GroupedPrefixGenerator.Builder<Integer>(new Random())
                .groupSize(groupSize)
                .prefixSize(prefixSize)
                .delegate(new StaticIntegerGenerator.Builder(new Random()).value(value).build())
                .build();
    }

    @Test
    void emitsValueForThePrefixAndNullForTheRestOfEachGroup() {
        int groupSize = 10;
        int prefixSize = 3;
        GroupedPrefixGenerator<Integer> generator = generator(groupSize, prefixSize, 7);

        for (int group = 0; group < 5; group++) {
            for (int position = 0; position < groupSize; position++) {
                Integer value = generator.generate();
                if (position < prefixSize) {
                    assertEquals(7, value, "expected delegate value at group " + group + " position " + position);
                } else {
                    assertNull(value, "expected null at group " + group + " position " + position);
                }
            }
        }
    }

    @Test
    void fullPrefixNeverEmitsNull() {
        GroupedPrefixGenerator<Integer> generator = generator(8, 8, 1);
        for (int i = 0; i < 32; i++) {
            assertEquals(1, generator.generate());
        }
    }

    @Test
    void zeroPrefixAlwaysEmitsNull() {
        GroupedPrefixGenerator<Integer> generator = generator(8, 0, 1);
        for (int i = 0; i < 32; i++) {
            assertNull(generator.generate());
        }
    }

    @Test
    void rejectsInvalidPrefixSize() {
        assertThrows(IllegalArgumentException.class, () -> generator(5, 6, 1));
        assertThrows(IllegalArgumentException.class, () -> generator(0, 0, 1));
    }
}
