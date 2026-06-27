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

/**
 * Generates the TPC-C i_data / s_data values (clause 4.3.3.1): a random
 * alphabetic string of length 26..50 that, 10% of the time, contains the literal
 * "ORIGINAL" at a random position.
 */
public class DataColumnGenerator extends AbstractDataGenerator<String> {

    public static final String ORIGINAL = "ORIGINAL";

    @Override
    public String generate() {

        int dataLength = randomUtils.nextInt(26, 51);
        String data = randomUtils.randomAlphabetic(dataLength);

        if (randomUtils.nextInt(1, 101) <= 10) {
            int start = randomUtils.nextInt(0, data.length() - ORIGINAL.length() + 1);
            data = data.substring(0, start) + ORIGINAL + data.substring(start + ORIGINAL.length());
        }

        return data;
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder<String> {

        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public DataColumnGenerator build() {
            return new DataColumnGenerator(random);
        }
    }

    private DataColumnGenerator(RandomGenerator random) {
        super(random);
    }
}
