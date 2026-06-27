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

class CompositeKeyComponentGeneratorTest {

    @Test
    void enumeratesEveryPairOfATwoDimensionalCartesianProductExactlyOnce() {
        int warehouses = 2;
        int items = 3;

        // warehouse outermost: repeat = items, cycle = warehouses
        CompositeKeyComponentGenerator outer =
                new CompositeKeyComponentGenerator.Builder(new Random()).start(1).repeat(items).cycle(warehouses).build();
        // item innermost: repeat = 1, cycle = items
        CompositeKeyComponentGenerator inner =
                new CompositeKeyComponentGenerator.Builder(new Random()).start(1).repeat(1).cycle(items).build();

        Set<String> pairs = new HashSet<>();
        for (int n = 0; n < warehouses * items; n++) {
            pairs.add(outer.generate() + "," + inner.generate());
        }

        assertEquals(warehouses * items, pairs.size(), "every (w, i) pair should be unique");
        assertEquals(Set.of("1,1", "1,2", "1,3", "2,1", "2,2", "2,3"), pairs);
    }

    @Test
    void outerComponentHoldsValueForRepeatRowsThenWraps() {
        CompositeKeyComponentGenerator outer =
                new CompositeKeyComponentGenerator.Builder(new Random()).start(1).repeat(3).cycle(2).build();

        int[] expected = {1, 1, 1, 2, 2, 2, 1, 1, 1};
        for (int value : expected) {
            assertEquals(value, outer.generate().intValue());
        }
    }
}
