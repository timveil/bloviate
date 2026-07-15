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
 * Generates random {@link java.sql.Timestamp} values for binding to JDBC {@code TIMESTAMP} columns,
 * drawn uniformly from a millisecond instant in the half-open range {@code [start, end)} (start
 * inclusive, end exclusive). By default the range is centered on
 * {@link AbstractBuilder#DEFAULT_REFERENCE}, spanning from 100 days before it up to (but excluding)
 * 100 days after it. Backed by the builder's seeded {@link java.util.random.RandomGenerator}, so the
 * same seed yields identical output.
 */
public class SqlTimestampGenerator extends AbstractDataGenerator<Timestamp> {

    private final long startMillisInclusive;
    private final long endMillisExclusive;
    private LongGenerator longGenerator;

    @Override
    public Timestamp generate() {

        Long randomTime = longGenerator.generate();

        return Timestamp.from(Instant.ofEpochMilli(randomTime));
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Timestamp value) throws SQLException {
        statement.setTimestamp(parameterIndex, value);
    }

    @Override
    public Timestamp get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTimestamp(columnIndex);
    }

    /**
     * Builder for {@link SqlTimestampGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<Timestamp> {

        private Timestamp startInclusive = Timestamp.from(DEFAULT_REFERENCE.minus(100, ChronoUnit.DAYS));
        private Timestamp endExclusive = Timestamp.from(DEFAULT_REFERENCE.plus(100, ChronoUnit.DAYS));

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
         * @param start the earliest possible timestamp, inclusive. Defaults to
         *              {@link AbstractBuilder#DEFAULT_REFERENCE} minus 100 days.
         * @return this builder, for chaining
         */
        public Builder start(Timestamp start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range.
         *
         * @param end the timestamp one millisecond past the latest possible value, exclusive.
         *            Defaults to {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 days.
         * @return this builder, for chaining
         */
        public Builder end(Timestamp end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public SqlTimestampGenerator build() {
            return new SqlTimestampGenerator(this);
        }
    }

    private SqlTimestampGenerator(Builder builder) {
        super(builder.random);
        this.startMillisInclusive = builder.startInclusive.toInstant().toEpochMilli();
        this.endMillisExclusive = builder.endExclusive.toInstant().toEpochMilli();
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
