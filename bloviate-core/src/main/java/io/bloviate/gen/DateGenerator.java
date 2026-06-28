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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.random.RandomGenerator;

/**
 * Generates random {@link java.util.Date} values drawn uniformly from a millisecond instant in the
 * half-open range {@code [start, end)} (start inclusive, end exclusive). By default the range spans
 * from the Unix {@linkplain java.time.Instant#EPOCH epoch} up to (but excluding)
 * {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 days. Backed by the builder's seeded
 * {@link java.util.random.RandomGenerator}, so the same seed yields identical output.
 */
public class DateGenerator extends AbstractDataGenerator<Date> {


    private final LongGenerator longGenerator;

    @Override
    public Date generate() {

        Long randomTime = longGenerator.generate();

        return new Date(randomTime);
    }

    @Override
    public Date get(ResultSet resultSet, int columnIndex) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnIndex);
        return timestamp != null ? new Date(timestamp.getTime()) : null;
    }

    /**
     * Builder for {@link DateGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<Date> {

        private Date startInclusive = Date.from(Instant.EPOCH);
        private Date endExclusive = Date.from(DEFAULT_REFERENCE.plus(100, ChronoUnit.DAYS));

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
         * @param start the earliest possible date, inclusive. Defaults to the Unix
         *              {@linkplain java.time.Instant#EPOCH epoch}.
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
        public DateGenerator build() {
            return new DateGenerator(this);
        }
    }

    private DateGenerator(Builder builder) {
        super(builder.random);

        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
