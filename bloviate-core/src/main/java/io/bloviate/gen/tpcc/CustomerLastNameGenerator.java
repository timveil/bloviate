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

package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractBuilder;
import io.bloviate.gen.AbstractDataGenerator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates TPC-C customer last names (clause 4.3.2.3): three concatenated
 * syllables selected from a fixed set by a number in {@code [0, 999]}.
 * The result is at most 15 characters, fitting the {@code c_last varchar(16)} column.
 *
 * <p>By default the number is drawn via {@code NURand} for every row. To match the
 * load procedure (clause 4.3.3.1), configure {@link Builder#groupSize(int) groupSize}
 * (the customers per district) and {@link Builder#enumeratedCount(int) enumeratedCount}
 * (typically {@value #MAX_ENUMERATED}): the first {@code enumeratedCount} customers of
 * each district then enumerate the numbers {@code 0..enumeratedCount-1} deterministically
 * — guaranteeing every distinct last name is present — and only the remaining customers
 * use {@code NURand}.
 */
public class CustomerLastNameGenerator extends AbstractDataGenerator<String> {

    private static final String[] SYLLABLES = {
            "BAR", "OUGHT", "ABLE", "PRI", "PRES", "ESE", "ANTI", "CALLY", "ATION", "EING"
    };

    // The C constant used while loading data, which must be in the range [0, 255] (clause 2.1.6.1).
    // Only the load-time constant is relevant to a data generator; the run-time constant is unused.
    private static final int C_LOAD = 157;

    /** The spec enumerates last-name numbers {@code [0, 999]} for the first 1000 customers per district. */
    public static final int MAX_ENUMERATED = 1000;

    private final int groupSize;       // customers per district; only used when enumeratedCount > 0
    private final int enumeratedCount; // first-N customers per district that enumerate 0..enumeratedCount-1
    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public String generate() {
        int num;
        if (enumeratedCount > 0) {
            int position = (int) (counter.getAndIncrement() % groupSize); // position within the district
            num = position < enumeratedCount ? position : TPCCUtils.nonUniformRandom(255, C_LOAD, 0, 999, randomUtils);
        } else {
            num = TPCCUtils.nonUniformRandom(255, C_LOAD, 0, 999, randomUtils);
        }
        return lastName(num);
    }

    /**
     * Builds the TPC-C last name for a number in {@code [0, 999]} by indexing the syllable
     * set with its three decimal digits.
     *
     * @param num a value in {@code [0, 999]}
     * @return the concatenated three-syllable last name
     */
    public static String lastName(int num) {
        return SYLLABLES[num / 100] + SYLLABLES[(num / 10) % 10] + SYLLABLES[num % 10];
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Builds {@link CustomerLastNameGenerator} instances. */
    public static class Builder extends AbstractBuilder<String> {

        private int groupSize = 0;
        private int enumeratedCount = 0;

        /**
         * Creates a builder.
         *
         * @param random the random generator backing the produced generator
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * The number of customers per district, used to locate each row's position within its
         * district when {@link #enumeratedCount(int)} is set.
         *
         * @param groupSize the number of customers per district
         * @return this builder
         */
        public Builder groupSize(int groupSize) {
            this.groupSize = groupSize;
            return this;
        }

        /**
         * The number of customers at the start of each district whose last name is enumerated
         * deterministically ({@code 0..enumeratedCount-1}); the rest use {@code NURand}. Must be
         * in {@code [0, }{@value #MAX_ENUMERATED}{@code ]}; {@code 0} disables enumeration.
         *
         * @param enumeratedCount the number of deterministically enumerated customers per district
         * @return this builder
         */
        public Builder enumeratedCount(int enumeratedCount) {
            this.enumeratedCount = enumeratedCount;
            return this;
        }

        @Override
        public CustomerLastNameGenerator build() {
            return new CustomerLastNameGenerator(random, groupSize, enumeratedCount);
        }
    }

    private CustomerLastNameGenerator(RandomGenerator random, int groupSize, int enumeratedCount) {
        super(random);
        if (enumeratedCount < 0 || enumeratedCount > MAX_ENUMERATED) {
            throw new IllegalArgumentException("enumeratedCount must be in [0, " + MAX_ENUMERATED + "]: " + enumeratedCount);
        }
        if (enumeratedCount > 0 && groupSize < 1) {
            throw new IllegalArgumentException("groupSize must be >= 1 when enumeratedCount > 0");
        }
        this.groupSize = groupSize;
        this.enumeratedCount = enumeratedCount;
    }
}
