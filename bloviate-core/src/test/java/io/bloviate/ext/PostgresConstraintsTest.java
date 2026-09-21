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
import io.bloviate.gen.TruncatedDateGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code pg_get_constraintdef} parser — the riskiest part of issue #479 — using
 * the verbose, normalized forms PostgreSQL actually emits, and the forms CockroachDB emits for the
 * same constraints (issue #633).
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

    // ---------------------------------------------------------------------------------------------
    // first-of-period dates and the mis-parse guard (issue #619)
    // ---------------------------------------------------------------------------------------------

    /**
     * Issue #614 / #619. The definition is the exact text PostgreSQL 18 stores (see
     * {@code PostgresPartitionedSchemaTest}) for a first-of-month CHECK on a {@code date} column. Its
     * quoted function argument and lack of {@code <} or {@code >} used to make the categorical branch
     * read it as the allowed values {@code ["month"]}, so the engine generated the string {@code month}
     * for a date column. It is now read as what it is.
     */
    @Test
    void quotedFunctionArgumentIsNotReadAsAValueList() {
        ColumnConstraint c = PostgresConstraints.parseCheck(
                "CHECK ((date_trunc('month'::text, (billing_month)::timestamp with time zone) = billing_month))");

        assertFalse(c.hasAllowedValues(), "'month' is the argument to date_trunc, not an allowed value: " + c);
        assertEquals(TruncatedDateGenerator.Unit.MONTH, c.dateTruncation());
    }

    @Test
    void parsesDateTruncOnDateTimestampAndTimestamptzColumns() {
        // date column: the column is cast to timestamptz; timestamp / timestamptz columns are bare
        for (String def : List.of(
                "CHECK ((date_trunc('month'::text, (billing_month)::timestamp with time zone) = billing_month))",
                "CHECK ((date_trunc('month'::text, created_at) = created_at))",
                "CHECK ((date_trunc('month'::text, \"Billing Month\") = \"Billing Month\"))",
                "CHECK ((date_trunc('MONTH'::text, created_at) = created_at)) NOT VALID")) {
            ColumnConstraint c = PostgresConstraints.parseCheck(def);
            assertEquals(TruncatedDateGenerator.Unit.MONTH, c.dateTruncation(), def);
            assertTrue(c.hasDateTruncation(), def);
        }
    }

    @Test
    void parsesDateTruncQuarterAndYear() {
        assertEquals(TruncatedDateGenerator.Unit.QUARTER, PostgresConstraints.parseCheck(
                "CHECK ((date_trunc('quarter'::text, created_at) = created_at))").dateTruncation());
        assertEquals(TruncatedDateGenerator.Unit.YEAR, PostgresConstraints.parseCheck(
                "CHECK ((date_trunc('year'::text, (d)::timestamp with time zone) = d))").dateTruncation());
    }

    @Test
    void rejectsDateTruncUnitsThatAreNotFirstOfAPeriod() {
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('day'::text, created_at) = created_at))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('week'::text, created_at) = created_at))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('hour'::text, created_at) = created_at))"));
    }

    @Test
    void rejectsDateTruncComparedWithAnotherColumnOrExpression() {
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('month'::text, a) = b))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('month'::text, a) > a))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('month'::text, a) = a) AND (a > '2020-01-01'::date))"));
    }

    @Test
    void parsesExtractDayEqualsOne() {
        // the exact text PostgreSQL 18 stores for a date column
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK ((EXTRACT(day FROM period_start) = (1)::numeric))");
        assertEquals(TruncatedDateGenerator.Unit.MONTH, c.dateTruncation());
        // timestamptz column, and the pre-PostgreSQL-14 date_part spelling
        assertEquals(TruncatedDateGenerator.Unit.MONTH, PostgresConstraints.parseCheck(
                "CHECK ((EXTRACT(day FROM (ts)::timestamp with time zone) = (1)::numeric))").dateTruncation());
        assertEquals(TruncatedDateGenerator.Unit.MONTH, PostgresConstraints.parseCheck(
                "CHECK ((date_part('day'::text, ts) = (1)::double precision))").dateTruncation());
    }

    @Test
    void rejectsExtractOfOtherFieldsOrValues() {
        assertNull(PostgresConstraints.parseCheck("CHECK ((EXTRACT(day FROM d) = (2)::numeric))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((EXTRACT(dow FROM d) = (1)::numeric))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((EXTRACT(month FROM d) = (1)::numeric))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((EXTRACT(day FROM d) >= (1)::numeric))"));
    }

    @Test
    void neverReadsAFunctionCallAsAValueListOrRange() {
        // function of the column: the literals are function arguments / a different value space
        assertNull(PostgresConstraints.parseCheck("CHECK ((lower((status)::text) = ANY (ARRAY['a'::text, 'b'::text])))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((upper((code)::text) = 'A'::text))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((length((name)::text) >= 1) AND (length((name)::text) <= 10))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((abs(delta) >= 1) AND (abs(delta) <= 5))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((to_char(d, 'YYYY'::text) = '2020'::text))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((btrim((name)::text, ' '::text) = (name)::text))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((date_trunc('month'::text, a) = ANY (ARRAY['x'::text])))"));
    }

    @Test
    void neverReadsArithmeticOrMultiColumnComparisonsAsARange() {
        assertNull(PostgresConstraints.parseCheck("CHECK (((x * 2) >= 10) AND ((x * 2) <= 20))"));
        assertNull(PostgresConstraints.parseCheck("CHECK (((x + 1) >= 10) AND (x <= 20))"));
        assertNull(PostgresConstraints.parseCheck("CHECK (((a >= 1) AND (b <= 5)))"));
    }

    @Test
    void quotedLiteralContentIsNeverReadAsSyntax() {
        // " not ", "(" and "<>" inside a literal are just text
        ColumnConstraint c = PostgresConstraints.parseCheck(
                "CHECK (((status)::text = ANY (ARRAY['do not ship'::text, 'a<>b'::text, 'f(x)'::text, 'a,b'::text])))");
        assertEquals(List.of("do not ship", "a<>b", "f(x)", "a,b"), c.allowedValues());
    }

    @Test
    void textCheckWithACastOnTheColumnStillMatches() {
        // a cast on the column IS how PostgreSQL stores a plain text check, so it must keep working
        assertEquals(List.of("a"), PostgresConstraints.parseCheck("CHECK (((status)::text = 'a'::text))").allowedValues());
        assertEquals(List.of("a", "b"), PostgresConstraints.parseCheck(
                "CHECK (((status)::text = ANY ((ARRAY['a'::character varying, 'b'::character varying])::text[])))").allowedValues());
        assertEquals(List.of("A", "B"), PostgresConstraints.parseCheck(
                "CHECK (((grade)::bpchar = ANY (ARRAY['A'::bpchar, 'B'::bpchar])))").allowedValues());
        assertEquals(List.of("A", "B"), PostgresConstraints.parseCheck("CHECK (grade IN ('A', 'B'))").allowedValues());
    }

    @Test
    void escapedQuotesInAllowedValuesAreUnescaped() {
        assertEquals(List.of("it's", "b"), PostgresConstraints.parseCheck(
                "CHECK ((name = ANY (ARRAY['it''s'::text, 'b'::text])))").allowedValues());
    }

    @Test
    void rejectsAMixOfLiteralsAndOtherExpressions() {
        assertNull(PostgresConstraints.parseCheck("CHECK ((status = ANY (ARRAY['a'::text, other_col])))"));
        assertNull(PostgresConstraints.parseCheck("CHECK ((status = 'a'::text) AND (kind = 'b'::text))"));
    }

    @Test
    void stillParsesRangesWithNegativeAndQuotedNumbers() {
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK (((temp >= '-40'::integer) AND (temp <= 60)))");
        assertEquals(0, new BigDecimal("-40").compareTo(c.min()));
        assertEquals(0, new BigDecimal("60").compareTo(c.max()));
        c = PostgresConstraints.parseCheck("CHECK ((((score)::numeric >= (0)::numeric) AND ((score)::numeric <= (100)::numeric)))");
        assertEquals(0, new BigDecimal("100").compareTo(c.max()));
    }

    // ---------------------------------------------------------------------------------------------
    // CockroachDB spellings of the same constraints (issue #633)
    // ---------------------------------------------------------------------------------------------

    /**
     * CockroachDB stores {@code BETWEEN} verbatim, where PostgreSQL expands it into a pair of
     * comparisons before storing the definition. It is always an inclusive closed range.
     */
    @Test
    void parsesCockroachBetween() {
        ColumnConstraint rating = PostgresConstraints.parseCheck("CHECK ((rating BETWEEN 1 AND 5))");
        assertEquals(0, new BigDecimal("1").compareTo(rating.min()));
        assertEquals(0, new BigDecimal("5").compareTo(rating.max()));
        assertTrue(rating.minInclusive());
        assertTrue(rating.maxInclusive());

        ColumnConstraint amount = PostgresConstraints.parseCheck("CHECK ((amount BETWEEN 0 AND 9999.99))");
        assertEquals(0, new BigDecimal("0").compareTo(amount.min()));
        assertEquals(0, new BigDecimal("9999.99").compareTo(amount.max()));
    }

    @Test
    void rejectsBetweenFormsThatCannotBeSatisfiedByConstruction() {
        assertNull(PostgresConstraints.parseCheck("CHECK ((rating NOT BETWEEN 1 AND 5))"), "negation");
        assertNull(PostgresConstraints.parseCheck("CHECK ((rating BETWEEN SYMMETRIC 5 AND 1))"), "symmetric bounds");
        assertNull(PostgresConstraints.parseCheck("CHECK ((rating BETWEEN 5 AND 1))"), "empty range");
        assertNull(PostgresConstraints.parseCheck("CHECK ((a BETWEEN b AND c))"), "column bounds");
        assertNull(PostgresConstraints.parseCheck("CHECK ((length(code) BETWEEN 1 AND 5))"), "function of the column");
        assertNull(PostgresConstraints.parseCheck("CHECK ((d BETWEEN '2020-01-01' AND '2020-12-31'))"), "non-numeric bounds");
    }

    /** CockroachDB writes {@code extract} with a comma and a quoted unit, not the standard FROM form. */
    @Test
    void parsesCockroachExtractCommaForm() {
        ColumnConstraint c = PostgresConstraints.parseCheck("CHECK ((extract('day'::STRING, d) = 1.0))");

        assertEquals(TruncatedDateGenerator.Unit.MONTH, c.dateTruncation());
        assertFalse(c.hasAllowedValues(), "'day' is the argument to extract, not an allowed value: " + c);
    }

    @Test
    void parsesCockroachCastsOnEveryCategoricalAndDateShape() {
        assertEquals(TruncatedDateGenerator.Unit.MONTH,
                PostgresConstraints.parseCheck("CHECK ((date_trunc('month'::STRING, d) = d))").dateTruncation());
        assertEquals(List.of("A", "B"),
                PostgresConstraints.parseCheck("CHECK ((grade IN ('A'::STRING, 'B'::STRING)))").allowedValues());
        assertEquals(List.of("1", "2", "3"),
                PostgresConstraints.parseCheck("CHECK ((priority IN (1, 2, 3)))").allowedValues());
        assertEquals(List.of("a"),
                PostgresConstraints.parseCheck("CHECK ((status = 'a'::STRING))").allowedValues());
    }

    /** Some CockroachDB builds write the annotation form of a cast, with three colons. */
    @Test
    void parsesTripleColonCasts() {
        assertEquals(TruncatedDateGenerator.Unit.MONTH,
                PostgresConstraints.parseCheck("CHECK ((date_trunc('month':::STRING, d) = d))").dateTruncation());
        assertEquals(TruncatedDateGenerator.Unit.MONTH,
                PostgresConstraints.parseCheck("CHECK ((extract('day':::STRING, d) = 1.0))").dateTruncation());
        assertEquals(List.of("A", "B"),
                PostgresConstraints.parseCheck("CHECK ((grade IN ('A':::STRING, 'B':::STRING)))").allowedValues());
    }
}
