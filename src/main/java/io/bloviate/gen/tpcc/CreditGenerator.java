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
import io.bloviate.util.SeededRandomUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

/**
 * Generates the TPC-C customer credit rating (clause 4.3.2.7): "GC" (good credit)
 * 90% of the time and "BC" (bad credit) 10% of the time.
 */
public class CreditGenerator extends AbstractDataGenerator<String> {

    public static final String GOOD_CREDIT = "GC";
    public static final String BAD_CREDIT = "BC";

    @Override
    public String generate() {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        return randomUtils.nextInt(1, 101) <= 10 ? BAD_CREDIT : GOOD_CREDIT;
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
        public CreditGenerator build() {
            return new CreditGenerator(random);
        }
    }

    private CreditGenerator(Random random) {
        super(random);
    }
}
