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

import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruncatedDateGeneratorTest {

    private static TruncatedDateGenerator.Builder builder(long seed) {
        return new TruncatedDateGenerator.Builder(RandomGenerators.create(seed));
    }

    private static List<LocalDate> draw(TruncatedDateGenerator generator, int count) {
        List<LocalDate> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(generator.generate());
        }
        return values;
    }

    @Test
    void everyDefaultValueIsTheFirstOfAMonthInTheDefaultWindow() {
        TruncatedDateGenerator generator = builder(1).build();
        Set<LocalDate> distinct = new HashSet<>();

        for (LocalDate value : draw(generator, 20_000)) {
            assertEquals(1, value.getDayOfMonth(), value.toString());
            assertTrue(!value.isBefore(LocalDate.of(2015, 1, 1)) && value.isBefore(LocalDate.of(2025, 1, 1)), value.toString());
            distinct.add(value);
        }
        // uniform over the 120 months of the default window, so 20k draws hit them all
        assertEquals(120, distinct.size());
    }

    @Test
    void sameSeedGivesTheSameSequenceAndDifferentSeedsDiffer() {
        List<LocalDate> first = draw(builder(42).build(), 200);
        List<LocalDate> second = draw(builder(42).build(), 200);
        List<LocalDate> other = draw(builder(43).build(), 200);

        assertEquals(first, second);
        assertTrue(!first.equals(other), "a different seed must give a different sequence");
    }

    @Test
    void outputIsPinnedForASeed() {
        // guards the seed-reproducibility invariant: this sequence must not change within a version
        assertEquals(List.of(LocalDate.parse("2021-07-01"), LocalDate.parse("2020-02-01"), LocalDate.parse("2020-01-01"),
                        LocalDate.parse("2023-07-01"), LocalDate.parse("2016-04-01")),
                draw(builder(42).build(), 5));
    }

    @Test
    void reseedReplaysTheSameSequence() {
        TruncatedDateGenerator generator = builder(1).build();
        generator.reseed(7);
        List<LocalDate> first = draw(generator, 50);
        generator.reseed(7);

        assertEquals(first, draw(generator, 50));
    }

    @Test
    void isPositionable() {
        assertTrue(builder(1).build().positionable());
    }

    @Test
    void aStartInTheMiddleOfAMonthRoundsUpToTheNextFirst() {
        // [2020-01-15, 2020-04-01) holds the month starts Feb 1 and Mar 1 only
        TruncatedDateGenerator generator = builder(3).start(LocalDate.of(2020, 1, 15)).end(LocalDate.of(2020, 4, 1)).build();

        assertEquals(Set.of(LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1)), new HashSet<>(draw(generator, 500)));
    }

    @Test
    void startIsInclusiveAndEndIsExclusive() {
        TruncatedDateGenerator generator = builder(3).start(LocalDate.of(2020, 1, 1)).end(LocalDate.of(2020, 3, 2)).build();

        assertEquals(Set.of(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), LocalDate.of(2020, 3, 1)),
                new HashSet<>(draw(generator, 500)));

        TruncatedDateGenerator exclusive = builder(3).start(LocalDate.of(2020, 1, 1)).end(LocalDate.of(2020, 3, 1)).build();
        assertEquals(Set.of(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1)), new HashSet<>(draw(exclusive, 500)));
    }

    @Test
    void quarterAndYearUnitsProducePeriodStarts() {
        TruncatedDateGenerator quarters = builder(5).unit(TruncatedDateGenerator.Unit.QUARTER)
                .start(LocalDate.of(2020, 2, 1)).end(LocalDate.of(2021, 2, 1)).build();
        // quarter starts on or after Feb 1 2020 and before Feb 1 2021: Apr, Jul, Oct 2020 and Jan 2021
        assertEquals(Set.of(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 7, 1), LocalDate.of(2020, 10, 1), LocalDate.of(2021, 1, 1)),
                new HashSet<>(draw(quarters, 500)));

        TruncatedDateGenerator years = builder(5).unit(TruncatedDateGenerator.Unit.YEAR).build();
        Set<LocalDate> seen = new HashSet<>(draw(years, 500));
        assertEquals(10, seen.size());
        assertTrue(seen.stream().allMatch(d -> d.getMonthValue() == 1 && d.getDayOfMonth() == 1));
    }

    @Test
    void truncateFindsThePeriodStart() {
        LocalDate date = LocalDate.of(2020, 8, 17);
        assertEquals(LocalDate.of(2020, 8, 1), TruncatedDateGenerator.Unit.MONTH.truncate(date));
        assertEquals(LocalDate.of(2020, 7, 1), TruncatedDateGenerator.Unit.QUARTER.truncate(date));
        assertEquals(LocalDate.of(2020, 1, 1), TruncatedDateGenerator.Unit.YEAR.truncate(date));
    }

    @Test
    void anEmptyRangeIsRejected() {
        TruncatedDateGenerator.Builder inBetweenFirsts = builder(1).start(LocalDate.of(2020, 1, 2)).end(LocalDate.of(2020, 1, 31));
        assertThrows(IllegalArgumentException.class, inBetweenFirsts::build);

        TruncatedDateGenerator.Builder backwards = builder(1).start(LocalDate.of(2021, 1, 1)).end(LocalDate.of(2020, 1, 1));
        assertThrows(IllegalArgumentException.class, backwards::build);

        TruncatedDateGenerator.Builder noQuarterStart = builder(1).unit(TruncatedDateGenerator.Unit.QUARTER)
                .start(LocalDate.of(2020, 2, 1)).end(LocalDate.of(2020, 4, 1));
        assertThrows(IllegalArgumentException.class, noQuarterStart::build);
    }

    @Test
    void dateColumnsAreBoundAsALocalDate() throws SQLException {
        Object[] bound = new Object[1];
        TruncatedDateGenerator generator = builder(9).build();

        generator.generateAndSet(null, capturing(bound), 1);

        LocalDate value = assertInstanceOfLocalDate(bound[0]);
        assertEquals(1, value.getDayOfMonth());
    }

    @Test
    void timestampColumnsAreBoundAsAZonelessMidnight() throws SQLException {
        Object[] bound = new Object[1];
        TruncatedDateGenerator generator = builder(9).timestamp(true).build();

        generator.generateAndSet(null, capturing(bound), 1);

        LocalDateTime value = (LocalDateTime) bound[0];
        assertEquals(1, value.getDayOfMonth());
        assertEquals(LocalDateTime.of(value.toLocalDate(), java.time.LocalTime.MIDNIGHT), value);
    }

    @Test
    void generatesTextInTheColumnsFormat() {
        assertEquals("2021-07-01", builder(42).build().generateAsString());
        assertEquals("2021-07-01 00:00:00", builder(42).timestamp(true).build().generateAsString());
    }

    @Test
    void aDateColumnIsReadAsALocalDate() throws SQLException {
        LocalDate date = LocalDate.of(2020, 5, 1);

        assertEquals(date, builder(1).build().get(row(null, date), 1));
        assertNull(builder(1).build().get(row(null, null), 1));
    }

    @Test
    void aTimestampIsReadFromTheDatePartOfTheDriversText() throws SQLException {
        TruncatedDateGenerator generator = builder(1).timestamp(true).build();
        LocalDate expected = LocalDate.of(2020, 3, 1);

        for (String text : List.of("2020-03-01 00:00:00", "2020-03-01 00:00:00.000000", "2020-03-01T00:00:00",
                "2020-03-01 00:00:00+13", "2020-03-01 00:00:00-08", "2020-03-01 00:00:00+05:30",
                "2020-03-01 00:00:00+05:30:15", "2020-03-01T00:00:00Z", "2020-03-01 00:00", "2020-03-01")) {
            assertEquals(expected, generator.get(row(text, null), 1), text);
        }
    }

    @Test
    void theTextWinsOverTheTypedValueSoAUtcNormalisedOffsetCannotShiftTheDate() throws SQLException {
        // what pgjdbc returns for a timestamptz written as midnight on 1 March in a Pacific/Auckland session:
        // the text is in the session zone, getObject is a Timestamp in the JVM zone, and OffsetDateTime is UTC
        TruncatedDateGenerator generator = builder(1).timestamp(true).build();
        LocalDate expected = LocalDate.of(2020, 3, 1);

        assertEquals(expected, generator.get(row("2020-03-01 00:00:00+13", OffsetDateTime.parse("2020-02-29T11:00:00Z")), 1));
        assertEquals(expected, generator.get(row("2020-03-01 00:00:00+13", Timestamp.valueOf("2020-02-29 06:00:00")), 1));
    }

    @Test
    void aTimestampWithNullTextIsNull() throws SQLException {
        assertNull(builder(1).timestamp(true).build().get(row(null, LocalDateTime.of(2020, 3, 1, 0, 0)), 1));
    }

    @Test
    void anUnrecognisedTextFallsBackToTheTypedValue() throws SQLException {
        TruncatedDateGenerator generator = builder(1).timestamp(true).build();
        LocalDate expected = LocalDate.of(2020, 3, 1);

        assertEquals(expected, generator.get(row("1 Mar 2020", LocalDateTime.of(2020, 3, 1, 0, 0)), 1));
        assertEquals(expected, generator.get(row("1 Mar 2020", OffsetDateTime.parse("2020-03-01T00:00:00+13:00")), 1));
        assertEquals(expected, generator.get(row("1 Mar 2020", Timestamp.valueOf("2020-03-01 00:00:00")), 1));
        assertThrows(SQLDataException.class, () -> generator.get(row("1 Mar 2020", null), 1));
    }

    @Test
    void normalisesEveryRepresentationTheDriversUse() throws SQLException {
        LocalDate expected = LocalDate.of(2020, 3, 1);

        assertEquals(expected, TruncatedDateGenerator.toLocalDate(expected));
        assertEquals(expected, TruncatedDateGenerator.toLocalDate(LocalDateTime.of(2020, 3, 1, 0, 0)));
        assertEquals(expected, TruncatedDateGenerator.toLocalDate(OffsetDateTime.parse("2020-03-01T00:00:00+13:00")));
        // built and read back in the same (JVM default) zone, so the result does not depend on which zone that is
        assertEquals(expected, TruncatedDateGenerator.toLocalDate(Timestamp.valueOf("2020-03-01 00:00:00")));
        assertEquals(expected, TruncatedDateGenerator.toLocalDate(java.sql.Date.valueOf(expected)));
        assertEquals(expected, TruncatedDateGenerator.toLocalDate("2020-03-01 00:00:00+05:30"));
    }

    @Test
    void refusesWhatIsNotADate() {
        assertThrows(SQLDataException.class, () -> TruncatedDateGenerator.toLocalDate("tomorrow"));
        assertThrows(SQLDataException.class, () -> TruncatedDateGenerator.toLocalDate("2020-13-45 00:00:00"));
        assertThrows(SQLDataException.class, () -> TruncatedDateGenerator.toLocalDate(42));
    }

    private static LocalDate assertInstanceOfLocalDate(Object value) {
        assertTrue(value instanceof LocalDate, "expected a LocalDate but was " + value);
        return (LocalDate) value;
    }

    /** A result set whose column 1 renders as {@code text} and whose typed value is {@code object}. */
    private static java.sql.ResultSet row(String text, Object object) {
        return (java.sql.ResultSet) Proxy.newProxyInstance(
                TruncatedDateGeneratorTest.class.getClassLoader(),
                new Class<?>[]{java.sql.ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getString" -> text;
                    case "getObject" -> object;
                    default -> null;
                });
    }

    /** A statement that records the value handed to {@code setObject}. */
    private static PreparedStatement capturing(Object[] bound) {
        return (PreparedStatement) Proxy.newProxyInstance(
                TruncatedDateGeneratorTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setObject")) {
                        bound[0] = args[1];
                    }
                    return null;
                });
    }
}
