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

package io.bloviate.db;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnConstraintTest {

    @Test
    void canonicalConstructorCopiesAllowedValues() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        ColumnConstraint constraint = new ColumnConstraint(source, null, false, null, false);
        source.add("c");

        // the constraint holds its own copy, so the caller's later mutation is not visible
        assertEquals(List.of("a", "b"), constraint.allowedValues());
    }

    @Test
    void ofValuesCopiesAllowedValues() {
        List<String> source = new ArrayList<>(List.of("x"));

        ColumnConstraint constraint = ColumnConstraint.ofValues(source);
        source.clear();

        assertEquals(List.of("x"), constraint.allowedValues());
    }

    @Test
    void allowedValuesIsUnmodifiable() {
        ColumnConstraint constraint = ColumnConstraint.ofValues(new ArrayList<>(List.of("a")));

        assertThrows(UnsupportedOperationException.class, () -> constraint.allowedValues().add("b"));
    }

    @Test
    void nullAllowedValuesIsPreserved() {
        // null is the documented marker for "range constraint", not a degenerate empty set:
        // normalising it to List.of() would make hasAllowedValues() ambiguous
        ColumnConstraint range = ColumnConstraint.ofRange(BigDecimal.ONE, BigDecimal.TEN);

        assertNull(range.allowedValues());
        assertFalse(range.hasAllowedValues());
        assertTrue(range.hasBoundedRange());
    }

    @Test
    void nullElementsAreRejected() {
        List<String> withNull = Arrays.asList("a", null);

        // List.copyOf's contract; asserted so the behaviour is a decision rather than an accident
        assertThrows(NullPointerException.class,
                () -> new ColumnConstraint(withNull, null, false, null, false));
    }
}
