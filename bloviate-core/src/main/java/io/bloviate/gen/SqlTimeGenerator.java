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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;

/**
 * Generates random {@link java.sql.Time} values for binding to JDBC {@code TIME} columns, drawn
 * uniformly from a millisecond instant in the half-open range {@code [start, end)} (start inclusive,
 * end exclusive). By default the range spans from the {@linkplain java.time.Instant#EPOCH epoch} up
 * to (but excluding) {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 hours. Backed by the
 * builder's seeded {@link java.util.random.RandomGenerator}, so the same seed yields identical
 * output.
 */
public class SqlTimeGenerator extends AbstractDataGenerator<Time> {

    private final LongGenerator longGenerator;

    @Override
    public Time generate() {

        Long randomTime = longGenerator.generate();

        return new Time(randomTime);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Time value) throws SQLException {
        statement.setTime(parameterIndex, value);
    }

    @Override
    public Time get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTime(columnIndex);
    }

    /**
     * Builder for {@link SqlTimeGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<Time> {

        private Time startInclusive = new Time(Instant.EPOCH.toEpochMilli());
        private Time endExclusive = new Time(DEFAULT_REFERENCE.plus(100, ChronoUnit.HOURS).toEpochMilli());

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
         * @param start the earliest possible time, inclusive. Defaults to the
         *              {@linkplain java.time.Instant#EPOCH epoch}.
         * @return this builder, for chaining
         */
        public Builder start(Time start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range.
         *
         * @param end the time one millisecond past the latest possible value, exclusive. Defaults
         *            to {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 hours.
         * @return this builder, for chaining
         */
        public Builder end(Time end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public SqlTimeGenerator build() {
            return new SqlTimeGenerator(this);
        }
    }

    private SqlTimeGenerator(Builder builder) {
        super(builder.random);

        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
