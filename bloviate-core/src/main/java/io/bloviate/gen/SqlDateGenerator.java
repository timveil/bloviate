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


import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;

/**
 * Generates random {@link java.sql.Date} values for binding to JDBC {@code DATE} columns, drawn
 * uniformly from a millisecond instant in the half-open range {@code [start, end)} (start inclusive,
 * end exclusive). By default the range is centered on {@link AbstractBuilder#DEFAULT_REFERENCE},
 * spanning from 100 days before it up to (but excluding) 100 days after it. Backed by the builder's
 * seeded {@link java.util.random.RandomGenerator}, so the same seed yields identical output.
 *
 * <p>Drawing from a {@link Builder#window(RelativeWindow.Resolved) window} works in whole calendar
 * days instead: each value is one of the UTC dates in the window, whichever time zone the JVM runs in.
 */
public class SqlDateGenerator extends AbstractDataGenerator<Date> {

    // the bounds of the draw: epoch milliseconds, or epoch days when drawing from a window
    private final long startMillisInclusive;
    private final long endMillisExclusive;
    private final boolean wholeDays;
    private LongGenerator longGenerator;

    @Override
    public Date generate() {

        Long randomTime = longGenerator.generate();

        // Date.valueOf builds the value from calendar fields, so the millisecond instant it holds is midnight
        // of that date in the JVM's zone and differs between zones; the calendar date itself does not. The
        // driver renders a java.sql.Date in the same zone, so the date that reaches the database is the one
        // drawn here whatever the JVM zone is, unlike a millisecond draw whose date can shift with the zone
        return wholeDays ? Date.valueOf(LocalDate.ofEpochDay(randomTime)) : new Date(randomTime);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Date value) throws SQLException {
        TemporalBinding.setDate(statement, parameterIndex, value);
    }

    @Override
    public Date get(ResultSet resultSet, int columnIndex) throws SQLException {
        return TemporalBinding.getDate(resultSet, columnIndex);
    }

    /**
     * Builder for {@link SqlDateGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<Date> {

        private Date startInclusive = new Date(DEFAULT_REFERENCE.minus(100, ChronoUnit.DAYS).toEpochMilli());
        private Date endExclusive = new Date(DEFAULT_REFERENCE.plus(100, ChronoUnit.DAYS).toEpochMilli());
        private boolean wholeDays;
        private long startDay;
        private long endDay;

        /**
         * Constructs a new builder.
         *
         * @param random the seeded random generator backing the produced generator
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the inclusive lower bound of the generated range.
         *
         * @param start the earliest possible date, inclusive. Defaults to
         *              {@link AbstractBuilder#DEFAULT_REFERENCE} minus 100 days.
         * @return this builder, for chaining
         */
        public Builder start(Date start) {
            this.startInclusive = start;
            this.wholeDays = false;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range.
         *
         * @param end the date one millisecond past the latest possible value, exclusive. Defaults
         *            to {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 days.
         * @return this builder, for chaining
         */
        public Builder end(Date end) {
            this.endExclusive = end;
            this.wholeDays = false;
            return this;
        }

        /**
         * Draws from a {@link RelativeWindow} resolved against the fill's anchor instead of from
         * explicit bounds. A {@code DATE} has no time of day, so the window is read as whole UTC calendar
         * days: the value is one of the dates {@code d} with {@code window.startDate() <= d <
         * window.endDate()} (see {@link RelativeWindow.Resolved#startDate()}), drawn uniformly, and the
         * column holds exactly that date whatever time zone the JVM or the session uses. Calling
         * {@link #start} or {@link #end} afterwards returns to drawing from instants.
         *
         * @param window the resolved window, for example
         *               {@code RelativeWindow.withinLast("90d").resolve(context.asOf())}
         * @return this builder, for chaining
         * @throws IllegalArgumentException if the window contains no whole date, such as a few hours in
         *                                  the middle of a day
         * @since 3.7.0
         */
        public Builder window(RelativeWindow.Resolved window) {
            LocalDate first = window.startDate();
            LocalDate end = window.endDate();
            if (!first.isBefore(end)) {
                throw new IllegalArgumentException("the window [" + window.start() + ", " + window.end()
                        + ") contains no whole date");
            }
            this.startDay = first.toEpochDay();
            this.endDay = end.toEpochDay();
            this.wholeDays = true;
            return this;
        }

        @Override
        public SqlDateGenerator build() {
            return new SqlDateGenerator(this);
        }
    }

    private SqlDateGenerator(Builder builder) {
        super(builder.random);
        this.wholeDays = builder.wholeDays;
        this.startMillisInclusive = wholeDays ? builder.startDay : builder.startInclusive.getTime();
        this.endMillisExclusive = wholeDays ? builder.endDay : builder.endExclusive.getTime();
        buildDelegates();
    }

    private void buildDelegates() {
        this.longGenerator = new LongGenerator.Builder(random)
                .start(startMillisInclusive)
                .end(endMillisExclusive)
                .build();
    }

    @Override
    protected void onReseed() {
        buildDelegates();
    }

}
