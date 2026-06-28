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
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;

/**
 * Generates random {@link java.sql.Date} values for binding to JDBC {@code DATE} columns, drawn
 * uniformly from a millisecond instant in the half-open range {@code [start, end)} (start inclusive,
 * end exclusive). By default the range is centered on {@link AbstractBuilder#DEFAULT_REFERENCE},
 * spanning from 100 days before it up to (but excluding) 100 days after it. Backed by the builder's
 * seeded {@link java.util.random.RandomGenerator}, so the same seed yields identical output.
 */
public class SqlDateGenerator extends AbstractDataGenerator<Date> {

    private final LongGenerator longGenerator;

    @Override
    public Date generate() {

        Long randomTime = longGenerator.generate();

        return new Date(randomTime);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Date value) throws SQLException {
        statement.setDate(parameterIndex, value);
    }

    @Override
    public Date get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getDate(columnIndex);
    }

    /**
     * Builder for {@link SqlDateGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<Date> {

        private Date startInclusive = new Date(DEFAULT_REFERENCE.minus(100, ChronoUnit.DAYS).toEpochMilli());
        private Date endExclusive = new Date(DEFAULT_REFERENCE.plus(100, ChronoUnit.DAYS).toEpochMilli());

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
            return this;
        }

        @Override
        public SqlDateGenerator build() {
            return new SqlDateGenerator(this);
        }
    }

    private SqlDateGenerator(Builder builder) {
        super(builder.random);
        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
