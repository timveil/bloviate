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
import java.util.random.RandomGenerator;

/**
 * Generates time spans as a {@link String} in PostgreSQL {@code interval} input form,
 * {@code years-months days hours:minutes:seconds}, e.g. {@code 3-7 14 21:45:09}.
 *
 * <p>Each component is drawn from a fixed range: years {@code 1..9}, months {@code 1..11},
 * days {@code 1..29}, hours {@code 1..23}, minutes and seconds {@code 1..59} (all inclusive).
 * Output is seeded and therefore reproducible for a given random source.
 */
public class IntervalGenerator extends AbstractDataGenerator<String> {

    private final IntegerGenerator yearGenerator;
    private final IntegerGenerator monthGenerator;
    private final IntegerGenerator dayGenerator;
    private final IntegerGenerator hourGenerator;
    private final IntegerGenerator minuteGenerator;
    private final IntegerGenerator secondGenerator;

    @Override
    public String generate() {
        return String.format("%d-%d %d %d:%d:%d",
                yearGenerator.generate(),
                monthGenerator.generate(),
                dayGenerator.generate(),
                hourGenerator.generate(),
                minuteGenerator.generate(),
                secondGenerator.generate()
        );
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Fluent builder for {@link IntervalGenerator}. */
    public static class Builder extends AbstractBuilder<String> {

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public IntervalGenerator build() {
            return new IntervalGenerator(this);
        }
    }

    private IntervalGenerator(Builder builder) {
        super(builder.random);
        this.yearGenerator = new IntegerGenerator.Builder(builder.random).start(1).end(10).build();
        this.monthGenerator = new IntegerGenerator.Builder(builder.random).start(1).end(12).build();
        this.dayGenerator = new IntegerGenerator.Builder(builder.random).start(1).end(30).build();
        this.hourGenerator = new IntegerGenerator.Builder(builder.random).start(1).end(24).build();
        this.minuteGenerator = new IntegerGenerator.Builder(builder.random).start(1).end(60).build();
        this.secondGenerator = new IntegerGenerator.Builder(builder.random).start(1).end(60).build();
    }
}
