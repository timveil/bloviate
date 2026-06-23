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
import java.util.Random;

/**
 * Generates TPC-C customer last names (clause 4.3.2.3): three concatenated
 * syllables selected from a fixed set using a number drawn via {@code NURand}.
 * The result is at most 15 characters, fitting the {@code c_last varchar(16)} column.
 */
public class CustomerLastNameGenerator extends AbstractDataGenerator<String> {

    private static final String[] SYLLABLES = {
            "BAR", "OUGHT", "ABLE", "PRI", "PRES", "ESE", "ANTI", "CALLY", "ATION", "EING"
    };

    // The C constant used while loading data, which must be in the range [0, 255] (clause 2.1.6.1).
    // Only the load-time constant is relevant to a data generator; the run-time constant is unused.
    private static final int C_LOAD = 157;

    @Override
    public String generate() {
        int num = TPCCUtils.nonUniformRandom(255, C_LOAD, 0, 999, randomUtils);
        return SYLLABLES[num / 100] + SYLLABLES[(num / 10) % 10] + SYLLABLES[num % 10];
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder<String> {

        public Builder(Random random) {
            super(random);
        }

        @Override
        public CustomerLastNameGenerator build() {
            return new CustomerLastNameGenerator(random);
        }
    }

    private CustomerLastNameGenerator(Random random) {
        super(random);
    }
}
