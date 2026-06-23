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
import org.apache.commons.lang3.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

/**
 * Generates TPC-C zip codes (clause 4.3.2.7): four random digits followed by the
 * constant suffix "11111", producing a 9-character value.
 */
public class ZipCodeGenerator extends AbstractDataGenerator<String> {

    private static final String SUFFIX = "11111";

    @Override
    public String generate() {
        return StringUtils.leftPad(Integer.toString(randomUtils.nextInt(0, 10000)), 4, '0') + SUFFIX;
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
        public ZipCodeGenerator build() {
            return new ZipCodeGenerator(random);
        }
    }

    private ZipCodeGenerator(Random random) {
        super(random);
    }
}
