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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@code generateAsString()} on the array generators — the form used for
 * flat-file output. Previously {@link StringArrayGenerator} returned {@code null} (empty cell) and
 * {@link IntegerArrayGenerator} inherited {@code Integer[].toString()} (a Java identity string such
 * as {@code [Ljava.lang.Integer;@5a07e868}), so array columns were silently broken in CSV/TSV/pipe
 * output. Both must now emit a PostgreSQL array literal carrying the element values.
 */
class ArrayGeneratorAsStringTest {

    @Test
    void integerArrayRendersAsPostgresArrayLiteral() {
        IntegerArrayGenerator generator = new IntegerArrayGenerator.Builder(new Random())
                .length(5).build();

        for (int i = 0; i < 200; i++) {
            String value = generator.generateAsString();

            assertNotNull(value, "generateAsString must not be null");
            assertFalse(value.contains("[L") || value.contains("@"),
                    "must not be a Java array identity string: " + value);
            assertTrue(value.startsWith("{") && value.endsWith("}"),
                    "expected a {..} array literal: " + value);

            String[] elements = value.substring(1, value.length() - 1).split(",");
            assertEquals(5, elements.length, "expected 5 elements: " + value);
            for (String element : elements) {
                assertDoesNotThrow(() -> Integer.parseInt(element),
                        "each element must be an integer: " + value);
            }
        }
    }

    @Test
    void stringArrayRendersAsPostgresArrayLiteral() {
        StringArrayGenerator generator = new StringArrayGenerator.Builder(new Random())
                .length(4).elementLength(8).build();

        for (int i = 0; i < 200; i++) {
            String value = generator.generateAsString();

            assertNotNull(value, "generateAsString must not be null");
            assertFalse(value.contains("[L") || value.contains("@"),
                    "must not be a Java array identity string: " + value);
            assertTrue(value.startsWith("{") && value.endsWith("}"),
                    "expected a {..} array literal: " + value);

            String[] elements = value.substring(1, value.length() - 1).split(",");
            assertEquals(4, elements.length, "expected 4 elements: " + value);
            for (String element : elements) {
                assertEquals(8, element.length(), "each element keeps its length: " + value);
                assertTrue(element.chars().allMatch(Character::isLetter),
                        "each element is alphabetic: " + value);
            }
        }
    }
}
