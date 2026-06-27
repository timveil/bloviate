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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the issue #466 contract for {@link IndexedDataGenerator}: for every positional generator,
 * {@code seek(k)} followed by {@code generate()} reproduces exactly the value the plain sequential
 * {@code generate()} stream yields at index {@code k} — the property intra-table partitioning relies
 * on for key columns to stay byte-for-byte identical regardless of how rows are partitioned.
 */
class IndexedGeneratorSeekTest {

    private static final int TOTAL = 60;
    private static final long[] SEEK_POINTS = {0, 1, 2, 7, 11, 12, 23, 24, 35, 59};

    /**
     * Asserts that for each seek point {@code k}, a freshly built generator seeked to {@code k} and
     * then run to the end produces the same values as the reference sequential stream from {@code k}.
     */
    private static void assertSeekMatchesSequential(Supplier<DataGenerator<Integer>> factory) {
        DataGenerator<Integer> reference = factory.get();
        Integer[] expected = new Integer[TOTAL];
        for (int i = 0; i < TOTAL; i++) {
            expected[i] = reference.generate();
        }

        for (long k : SEEK_POINTS) {
            DataGenerator<Integer> seeker = factory.get();
            ((IndexedDataGenerator) seeker).seek(k);
            for (int i = (int) k; i < TOTAL; i++) {
                int row = i;
                assertEquals(expected[i], seeker.generate(),
                        () -> "seek(" + k + ") then generating to the end must match the sequential value at row " + row);
            }
        }
    }

    @Test
    void compositeKeyComponentSeekMatchesSequential() {
        assertSeekMatchesSequential(() ->
                new CompositeKeyComponentGenerator.Builder(new Random()).start(1).repeat(3).cycle(4).build());
    }

    @Test
    void sequentialIntegerSeekMatchesSequential() {
        assertSeekMatchesSequential(() ->
                new SequentialIntegerGenerator.Builder(new Random()).start(5).end(9).build());
    }

    @Test
    void childCountSeekMatchesSequential() {
        ChildCardinality cardinality = new ChildCardinality(1, 5, 42L);
        assertSeekMatchesSequential(() ->
                new ChildCountGenerator.Builder(new Random()).cardinality(cardinality).build());
    }

    @Test
    void groupedPermutationSeekMatchesSequential() {
        assertSeekMatchesSequential(() ->
                new GroupedPermutationGenerator.Builder(new Random()).groupSize(7).start(1).seed(99L).build());
    }

    @Test
    void childKeySequenceSeekMatchesSequential_fixedCardinality() {
        ChildCardinality cardinality = new ChildCardinality(3, 3, 7L);
        assertSeekMatchesSequential(() ->
                new ChildKeyComponentGenerator.Builder(new Random()).cardinality(cardinality).sequence().start(1).build());
    }

    @Test
    void childKeySequenceSeekMatchesSequential_variableCardinality() {
        ChildCardinality cardinality = new ChildCardinality(1, 4, 123L);
        assertSeekMatchesSequential(() ->
                new ChildKeyComponentGenerator.Builder(new Random()).cardinality(cardinality).sequence().start(1).build());
    }

    @Test
    void childKeyParentComponentSeekMatchesSequential_variableCardinality() {
        ChildCardinality cardinality = new ChildCardinality(0, 4, 55L);
        assertSeekMatchesSequential(() ->
                new ChildKeyComponentGenerator.Builder(new Random()).cardinality(cardinality).start(1).repeat(1).cycle(6).build());
    }

    @Test
    void groupedPrefixSeekMatchesSequential_staticDelegate() {
        // a non-positional (static) delegate: the null/non-null pattern and the constant value are
        // both a function of the absolute position, so seek is byte-identical
        assertSeekMatchesSequential(() ->
                new GroupedPrefixGenerator.Builder<Integer>(new Random())
                        .groupSize(10).prefixSize(3)
                        .delegate(new StaticIntegerGenerator.Builder(new Random()).value(7).build())
                        .build());
    }

    @Test
    void groupedPrefixSeekMatchesSequential_positionalDelegate() {
        // a positional delegate is itself sought, so the emitted prefix values track absolute position
        assertSeekMatchesSequential(() ->
                new GroupedPrefixGenerator.Builder<Integer>(new Random())
                        .groupSize(8).prefixSize(5)
                        .delegate(new SequentialIntegerGenerator.Builder(new Random()).start(100).end(199).build())
                        .build());
    }

    @Test
    void seekRejectsNegativeRowIndex() {
        IndexedDataGenerator generator =
                new CompositeKeyComponentGenerator.Builder(new Random()).start(1).repeat(1).cycle(3).build();
        assertThrows(IllegalArgumentException.class, () -> generator.seek(-1));
    }
}
