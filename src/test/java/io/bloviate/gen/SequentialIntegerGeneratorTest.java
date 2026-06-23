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
import static org.junit.jupiter.api.Assertions.assertThrows;

class SequentialIntegerGeneratorTest {

    @Test
    void rejectsAnInvalidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new SequentialIntegerGenerator.Builder(new Random()).start(5).end(3).build());
    }

    @Test
    void emitsInclusiveRangeThenWraps() {
        SequentialIntegerGenerator generator = new SequentialIntegerGenerator.Builder(new Random()).start(1).end(3).build();

        assertEquals(1, generator.generate().intValue());
        assertEquals(2, generator.generate().intValue());
        assertEquals(3, generator.generate().intValue());
        assertEquals(1, generator.generate().intValue());
        assertEquals(2, generator.generate().intValue());
    }

    @Test
    void producesUniqueValuesAcrossTheSpanBeforeWrapping() {
        int start = 10;
        int end = 30;

        SequentialIntegerGenerator generator = new SequentialIntegerGenerator.Builder(new Random()).start(start).end(end).build();

        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i <= (end - start); i++) {
            seen.add(generator.generate());
        }

        assertEquals(end - start + 1, seen.size());
        assertEquals(start, generator.generate().intValue());
    }
}
