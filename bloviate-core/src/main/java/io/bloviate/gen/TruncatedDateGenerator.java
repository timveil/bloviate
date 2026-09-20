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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;

/**
 * Generates the <em>first day of a period</em> &mdash; by default the first day of a month &mdash; for
 * columns that only admit such dates, for instance {@code CHECK (date_trunc('month', d) = d)} or
 * {@code CHECK (EXTRACT(day FROM d) = 1)}. Each value is drawn uniformly from the periods that start
 * in the half-open range {@code [start, end)}; by default that is the ten years from five years before
 * {@link AbstractBuilder#DEFAULT_REFERENCE} to five years after it (2015-01-01 up to, excluding,
 * 2025-01-01), so a monthly column has 120 distinct values to draw from. The window is anchored to
 * that fixed reference rather than the wall clock, so the same seed yields identical output on every
 * run. Backed by the builder's seeded {@link RandomGenerator}.
 *
 * <p>The value is a {@link LocalDate}, with no time zone, and is bound as one: a {@code DATE} column
 * receives exactly that calendar date. Call {@link Builder#timestamp(boolean) timestamp(true)} for a
 * {@code TIMESTAMP} or {@code TIMESTAMP WITH TIME ZONE} column: the value is then bound as a zone-less
 * {@link LocalDateTime} at midnight, which the database reads as midnight in <em>its</em> session time
 * zone. That is what a {@code date_trunc} check evaluates in, so the check holds whatever the session
 * zone is. (The one exception is a zone whose clocks skip midnight on the first of the period, such as
 * a DST change at 00:00: there the local time does not exist and the database moves it forward.)
 *
 * <p>Use it directly for an explicit column override, e.g.
 * {@code new TruncatedDateGenerator.Builder(random).unit(Unit.MONTH).build()}. The fill engine also
 * selects it by itself when it reads such a {@code CHECK} constraint from PostgreSQL.
 *
 * @since 3.5.0
 */
public class TruncatedDateGenerator extends AbstractDataGenerator<LocalDate> {

    private static final DateTimeFormatter TIMESTAMP_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** The length of the period whose first day is generated. */
    public enum Unit {

        /** The first day of a calendar month. */
        MONTH(1),

        /** The first day of a calendar quarter (January, April, July or October). */
        QUARTER(3),

        /** The first day of a calendar year. */
        YEAR(12);

        private final int months;

        Unit(int months) {
            this.months = months;
        }

        /**
         * The first day of the period containing {@code date}.
         *
         * @param date any date
         * @return the first day of the month, quarter or year that {@code date} falls in
         */
        public LocalDate truncate(LocalDate date) {
            int month = date.getMonthValue();
            return LocalDate.of(date.getYear(), ((month - 1) / months) * months + 1, 1);
        }

        /**
         * The first day of the period after the one starting on {@code periodStart}.
         *
         * @param periodStart the first day of a period
         * @return the first day of the following period
         */
        LocalDate next(LocalDate periodStart) {
            return periodStart.plusMonths(months);
        }

        int months() {
            return months;
        }
    }

    private final Unit unit;
    private final boolean timestamp;
    private final LocalDate firstPeriod;
    private final int periodCount;

    @Override
    public LocalDate generate() {
        return firstPeriod.plusMonths((long) randomUtils.nextInt(0, periodCount) * unit.months());
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, LocalDate value) throws SQLException {
        if (timestamp) {
            statement.setObject(parameterIndex, value.atStartOfDay());
        } else {
            statement.setObject(parameterIndex, value);
        }
    }

    @Override
    public LocalDate get(ResultSet resultSet, int columnIndex) throws SQLException {
        if (timestamp) {
            LocalDateTime value = resultSet.getObject(columnIndex, LocalDateTime.class);
            return value == null ? null : value.toLocalDate();
        }
        return resultSet.getObject(columnIndex, LocalDate.class);
    }

    @Override
    public String generateAsString() {
        LocalDate value = generate();
        return timestamp ? value.atStartOfDay().format(TIMESTAMP_TEXT) : value.toString();
    }

    /** Fluent builder for {@link TruncatedDateGenerator}. */
    public static class Builder extends AbstractBuilder<LocalDate> {

        private Unit unit = Unit.MONTH;
        private boolean timestamp;
        private LocalDate startInclusive = LocalDate.ofInstant(DEFAULT_REFERENCE, ZoneOffset.UTC).minusYears(5);
        private LocalDate endExclusive = LocalDate.ofInstant(DEFAULT_REFERENCE, ZoneOffset.UTC).plusYears(5);

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the period whose first day is generated. Defaults to {@link Unit#MONTH}.
         *
         * @param unit the period length
         * @return this builder, for chaining
         */
        public Builder unit(Unit unit) {
            this.unit = unit;
            return this;
        }

        /**
         * Sets the inclusive lower bound: the earliest period start that may be generated is the first
         * period start on or after this date. Defaults to five years before
         * {@link AbstractBuilder#DEFAULT_REFERENCE}.
         *
         * @param start the earliest date, inclusive
         * @return this builder, for chaining
         */
        public Builder start(LocalDate start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound: only period starts strictly before this date are generated.
         * Defaults to five years after {@link AbstractBuilder#DEFAULT_REFERENCE}.
         *
         * @param end the date after the latest possible value, exclusive
         * @return this builder, for chaining
         */
        public Builder end(LocalDate end) {
            this.endExclusive = end;
            return this;
        }

        /**
         * Chooses how the value is bound. {@code false} (the default) binds a {@link LocalDate} for a
         * {@code DATE} column; {@code true} binds midnight of that date as a zone-less
         * {@link LocalDateTime} for a {@code TIMESTAMP} or {@code TIMESTAMP WITH TIME ZONE} column.
         *
         * @param timestamp whether the target column holds a time of day
         * @return this builder, for chaining
         */
        public Builder timestamp(boolean timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds the generator.
         *
         * @return the generator
         * @throws IllegalArgumentException if no period starts in {@code [start, end)}
         */
        @Override
        public TruncatedDateGenerator build() {
            return new TruncatedDateGenerator(this);
        }
    }

    private TruncatedDateGenerator(Builder builder) {
        super(builder.random);
        this.unit = builder.unit;
        this.timestamp = builder.timestamp;

        LocalDate first = unit.truncate(builder.startInclusive);
        if (first.isBefore(builder.startInclusive)) {
            first = unit.next(first);
        }
        // whole months from the first period start to the end; a partial trailing month still holds
        // a month start only if the end is past it
        long months = ChronoUnit.MONTHS.between(first, builder.endExclusive);
        if (first.plusMonths(months).isBefore(builder.endExclusive)) {
            months++;
        }
        long periods = (months + unit.months() - 1) / unit.months();
        if (periods <= 0 || periods > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("no " + unit + " starts in [" + builder.startInclusive + ", "
                    + builder.endExclusive + ")");
        }
        this.firstPeriod = first;
        this.periodCount = (int) periods;
    }
}
