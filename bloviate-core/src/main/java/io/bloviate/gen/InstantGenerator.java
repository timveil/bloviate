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
import java.util.random.RandomGenerator;

/**
 * Generates random {@link java.time.Instant} values drawn uniformly from a millisecond instant in
 * the half-open range {@code [start, end)} (start inclusive, end exclusive). By default the range
 * spans from the {@linkplain java.time.Instant#EPOCH epoch} up to (but excluding)
 * {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 days. Backed by the builder's seeded
 * {@link java.util.random.RandomGenerator}, so the same seed yields identical output.
 */
public class InstantGenerator extends AbstractDataGenerator<Instant> {

    private final LongGenerator longGenerator;

    @Override
    public Instant generate() {

        Long randomTime = longGenerator.generate();

        return Instant.ofEpochMilli(randomTime);
    }

    @Override
    public Instant get(ResultSet resultSet, int columnIndex) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    /**
     * Builder for {@link InstantGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<Instant> {

        private Instant startInclusive = Instant.EPOCH;
        private Instant endExclusive = DEFAULT_REFERENCE.plus(100, ChronoUnit.DAYS);

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
         * @param start the earliest possible instant, inclusive. Defaults to the
         *              {@linkplain java.time.Instant#EPOCH epoch}.
         * @return this builder, for chaining
         */
        public Builder start(Instant start) {
            this.startInclusive = start;
            return this;
        }

        /**
         * Sets the exclusive upper bound of the generated range.
         *
         * @param end the instant one millisecond past the latest possible value, exclusive.
         *            Defaults to {@link AbstractBuilder#DEFAULT_REFERENCE} plus 100 days.
         * @return this builder, for chaining
         */
        public Builder end(Instant end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public InstantGenerator build() {
            return new InstantGenerator(this);
        }
    }

    private InstantGenerator(Builder builder) {
        super(builder.random);

        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.toEpochMilli())
                .end(builder.endExclusive.toEpochMilli())
                .build();
    }
}
