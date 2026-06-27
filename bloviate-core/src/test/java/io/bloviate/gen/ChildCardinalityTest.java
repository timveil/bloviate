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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildCardinalityTest {

    @Test
    void countStaysWithinInclusiveRangeAndIsDeterministic() {
        ChildCardinality cardinality = new ChildCardinality(5, 15, 0xABCDEFL);
        for (long k = 0; k < 10_000; k++) {
            int count = cardinality.count(k);
            assertTrue(count >= 5 && count <= 15, "out of range at " + k + ": " + count);
            assertEquals(count, cardinality.count(k), "non-deterministic at " + k);
        }
    }

    @Test
    void totalMatchesTheSumOfCounts() {
        ChildCardinality cardinality = new ChildCardinality(1, 9, 42);
        long expected = 0;
        for (long k = 0; k < 5_000; k++) {
            expected += cardinality.count(k);
        }
        assertEquals(expected, cardinality.total(5_000));
    }

    @Test
    void totalIsClosedFormWhenCountIsFixed() {
        ChildCardinality fixed = new ChildCardinality(7, 7, 1);
        assertEquals(7L * 1_000_000, fixed.total(1_000_000));
        for (long k = 0; k < 100; k++) {
            assertEquals(7, fixed.count(k));
        }
    }

    @Test
    void countGeneratorEmitsCardinalityCountPerParentInOrder() {
        ChildCardinality cardinality = new ChildCardinality(5, 15, 99);
        ChildCountGenerator generator = new ChildCountGenerator.Builder(new Random()).cardinality(cardinality).build();
        for (long k = 0; k < 1_000; k++) {
            assertEquals(cardinality.count(k), generator.generate().intValue(), "mismatch at parent " + k);
        }
    }

    @Test
    void childKeysAgreeWithParentCountsAndDecomposeRowMajor() {
        // parent key space: W=2 x D=2 x O=3 = 12 parents, row-major (warehouse outermost)
        int w = 2, d = 2, o = 3;
        long parents = (long) w * d * o;
        ChildCardinality cardinality = new ChildCardinality(1, 4, 7);

        long total = cardinality.total(parents);

        // child key generators mirror the parent key's start/repeat/cycle, plus a sequence column
        ChildKeyComponentGenerator wId = childKey(cardinality, 1, (long) d * o, w);
        ChildKeyComponentGenerator dId = childKey(cardinality, 1, o, d);
        ChildKeyComponentGenerator oId = childKey(cardinality, 1, 1, o);
        ChildKeyComponentGenerator supplyW = childKey(cardinality, 1, (long) d * o, w);
        ChildKeyComponentGenerator number = new ChildKeyComponentGenerator.Builder(new Random()).cardinality(cardinality).sequence().start(1).build();

        // collect the sequence numbers seen for each parent index
        List<List<Integer>> seqByParent = new ArrayList<>();
        for (int k = 0; k < parents; k++) {
            seqByParent.add(new ArrayList<>());
        }

        for (long row = 0; row < total; row++) {
            int wv = wId.generate();
            int dv = dId.generate();
            int ov = oId.generate();
            int sv = supplyW.generate();
            int nv = number.generate();

            assertEquals(wv, sv, "supply warehouse should equal the order warehouse");

            int parentIndex = (wv - 1) * (d * o) + (dv - 1) * o + (ov - 1);
            seqByParent.get(parentIndex).add(nv);
        }

        for (int k = 0; k < parents; k++) {
            int expectedCount = cardinality.count(k);
            List<Integer> seqs = seqByParent.get(k);
            assertEquals(expectedCount, seqs.size(), "row count for parent " + k + " must equal o_ol_cnt");
            // ol_number must be exactly 1..expectedCount in order
            for (int line = 0; line < expectedCount; line++) {
                assertEquals(line + 1, seqs.get(line).intValue(), "non-contiguous sequence for parent " + k);
            }
        }
    }

    private static ChildKeyComponentGenerator childKey(ChildCardinality cardinality, int start, long repeat, int cycle) {
        return new ChildKeyComponentGenerator.Builder(new Random())
                .cardinality(cardinality).start(start).repeat(repeat).cycle(cycle).build();
    }
}
