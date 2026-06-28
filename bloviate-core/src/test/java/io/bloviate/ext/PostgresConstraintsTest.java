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

package io.bloviate.ext;

import io.bloviate.db.ColumnConstraint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code pg_get_constraintdef} parser — the riskiest part of issue #479 — using
 * the verbose, normalized forms PostgreSQL actually emits.
 */
class PostgresConstraintsTest {

    @Test
    void parsesInclusiveIntegerRange() {
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK (((rating >= 1) AND (rating <= 5)))");
        assertEquals(0, new BigDecimal("1").compareTo(c.min()));
        assertEquals(0, new BigDecimal("5").compareTo(c.max()));
        assertTrue(c.minInclusive());
        assertTrue(c.maxInclusive());
        assertTrue(c.hasBoundedRange());
    }

    @Test
    void parsesNumericRangeWithCasts() {
        ColumnConstraint c = PostgresConstraints.parseCheck(
                "CHECK (((amount >= (0)::numeric) AND (amount <= (9999.99)::numeric)))");
        assertEquals(0, new BigDecimal("0").compareTo(c.min()));
        assertEquals(0, new BigDecimal("9999.99").compareTo(c.max()));
    }

    @Test
    void parsesExclusiveRange() {
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK ((x > 0) AND (x < 10))");
        assertEquals(0, new BigDecimal("0").compareTo(c.min()));
        assertEquals(0, new BigDecimal("10").compareTo(c.max()));
        assertFalse(c.minInclusive());
        assertFalse(c.maxInclusive());
    }

    @Test
    void parsesStringInList() {
        ColumnConstraint c = PostgresConstraints.parseCheck(
                "CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'SHIPPED'::character varying, 'CANCELLED'::character varying])::text[])))");
        assertEquals(List.of("NEW", "SHIPPED", "CANCELLED"), c.allowedValues());
    }

    @Test
    void parsesNumericInList() {
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK ((priority = ANY (ARRAY[1, 2, 3])))");
        assertEquals(List.of("1", "2", "3"), c.allowedValues());
    }

    @Test
    void parsesBareEquality() {
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK (((code)::text = 'A'::text))");
        assertEquals(List.of("A"), c.allowedValues());
    }

    @Test
    void rejectsNegationDisjunctionAndPatterns() {
        assertNull(PostgresConstraints.parseCheck("CHECK (((name)::text <> ''::text))"), "negation");
        assertNull(PostgresConstraints.parseCheck("CHECK (((a >= 1) OR (a <= 0)))"), "disjunction");
        assertNull(PostgresConstraints.parseCheck("CHECK (((email)::text ~~ '%@%'::text))"), "pattern");
    }

    @Test
    void rejectsOneSidedRange() {
        // a single bound can't be turned into a closed range generator, so it is not honored
        assertNull(PostgresConstraints.parseCheck("CHECK ((age >= 18))"));
    }
}
