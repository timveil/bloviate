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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExpressionGeneratorTest {

    private static DataGenerator<String> wrapped(String expression) {
        return SqlExpressionGenerator.of(new JsonbGenerator.Builder(new Random(1)).build(), expression);
    }

    @Test
    void reportsTheExpressionAndDelegatesTheValue() {
        DataGenerator<String> wrapped = wrapped("PARSE_JSON(?)");

        assertEquals("PARSE_JSON(?)", wrapped.valueExpression());
        assertTrue(wrapped.generate().startsWith("{"));
    }

    @Test
    void wrappingDoesNotChangeGeneratedValues() {
        // same seed, one wrapped and one not: wrapping affects how the value reaches the column,
        // never the value itself, so seed reproducibility must survive it
        DataGenerator<String> plain = new JsonbGenerator.Builder(new Random(42)).build();
        DataGenerator<String> wrapped = SqlExpressionGenerator.of(
                new JsonbGenerator.Builder(new Random(42)).build(), "PARSE_JSON(?)");

        for (int i = 0; i < 20; i++) {
            assertEquals(plain.generate(), wrapped.generate());
        }
    }

    @Test
    void rejectsAnExpressionThatWouldMisalignParameters() {
        // the engine binds one parameter per column by position, so anything other than exactly one
        // placeholder shifts every later column's value onto the wrong parameter
        assertThrows(IllegalArgumentException.class, () -> wrapped("PARSE_JSON('x')"));
        assertThrows(IllegalArgumentException.class, () -> wrapped("RANGE(?, ?)"));
        assertThrows(IllegalArgumentException.class, () -> wrapped(null));
        assertThrows(IllegalArgumentException.class,
                () -> SqlExpressionGenerator.of(null, "PARSE_JSON(?)"));
    }

    @Test
    void delegatesPositionable() {
        // answering for the delegate would either skip repositioning a positionable column or
        // reposition one that cannot survive it
        assertTrue(wrapped("PARSE_JSON(?)").positionable());
        assertFalse(SqlExpressionGenerator.of(new NotPositionable(), "CAST(? AS DATETIME)").positionable());
    }

    @Test
    void delegatesReseed() {
        DataGenerator<String> wrapped = wrapped("PARSE_JSON(?)");
        String first = wrapped.generate();

        wrapped.reseed(99);
        String afterReseed = wrapped.generate();
        wrapped.reseed(99);

        assertNotEquals(first, afterReseed);
        assertEquals(afterReseed, wrapped.generate());
    }

    // ------------------------------------------------------------------ IndexedDataGenerator

    @Test
    void keepsAnIndexedDelegateSeekable() {
        // TableFiller tests `instanceof IndexedDataGenerator` to decide how to position a
        // partitioned fill, so dropping the interface here would silently replay draws instead
        DataGenerator<Integer> wrapped = SqlExpressionGenerator.of(new Counter(), "CAST(? AS INT64)");

        assertInstanceOf(IndexedDataGenerator.class, wrapped);

        ((IndexedDataGenerator) wrapped).seek(7);
        assertEquals(7, wrapped.generate());
        assertEquals(8, wrapped.generate());
    }

    @Test
    void doesNotClaimSeekabilityForAPlainDelegate() {
        // the mirror-image failure: claiming the interface without a seekable delegate makes the
        // engine skip the repositioning that column actually needs
        assertFalse(SqlExpressionGenerator.of(new NotPositionable(), "CAST(? AS DATETIME)")
                instanceof IndexedDataGenerator);
    }

    @Test
    void aSeekedIndexedDelegateMatchesTheSequentialRun() {
        // the property partitioned fills depend on: seeking to row N produces exactly what the
        // sequential run produces at row N
        DataGenerator<Integer> sequential = SqlExpressionGenerator.of(new Counter(), "CAST(? AS INT64)");
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            expected.add(sequential.generate());
        }

        DataGenerator<Integer> partitioned = SqlExpressionGenerator.of(new Counter(), "CAST(? AS INT64)");
        ((IndexedDataGenerator) partitioned).seek(5);

        for (int i = 5; i < 10; i++) {
            assertEquals(expected.get(i), partitioned.generate());
        }
    }

    /** A minimal indexed generator: its value is its absolute row index. */
    private static final class Counter extends AbstractDataGenerator<Integer> implements IndexedDataGenerator {

        private long row;

        private Counter() {
            super(new Random(1));
        }

        @Override
        public Integer generate() {
            return (int) row++;
        }

        @Override
        public void seek(long rowIndex) {
            this.row = rowIndex;
        }

        @Override
        public Integer get(java.sql.ResultSet resultSet, int columnIndex) {
            throw new UnsupportedOperationException();
        }
    }

    /** A generator that opts out of positioning, as the datafaker integration does. */
    private static final class NotPositionable extends AbstractDataGenerator<String> {

        private NotPositionable() {
            super(new Random(1));
        }

        @Override
        public boolean positionable() {
            return false;
        }

        @Override
        public String generate() {
            return "x";
        }

        @Override
        public String get(java.sql.ResultSet resultSet, int columnIndex) {
            throw new UnsupportedOperationException();
        }
    }
}
