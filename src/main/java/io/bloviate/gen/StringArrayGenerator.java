/*
 * Copyright 2020 Tim Veil
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

import io.bloviate.util.SeededRandomUtils;

import java.sql.*;
import java.util.Random;

public class StringArrayGenerator extends AbstractDataGenerator<String[]> {

    private final int length;
    private final int elementLength;

    @Override
    public String[] generate() {
        String[] randomArray = new String[length];

        SeededRandomUtils randomUtils = new SeededRandomUtils(random);

        for (int i = 0; i < length; i++) {
            randomArray[i] = randomUtils.randomAlphabetic(elementLength);
        }

        return randomArray;

    }

    @Override
    public String generateAsString() {
        return null;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setArray(parameterIndex, connection.createArrayOf(JDBCType.VARCHAR.getName(), (String[]) value));
    }

    @Override
    public String[] get(ResultSet resultSet, int columnIndex) throws SQLException {
        return null;
    }

    public static class Builder extends AbstractBuilder {

        private int length = 3;
        private int elementLength = 10;
        private DataGenerator<? extends String> elementGenerator;

        public Builder(Random random) {
            super(random);
        }

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder elementLength(int elementLength) {
            this.elementLength = elementLength;
            return this;
        }

        @Override
        public StringArrayGenerator build() {
            return new StringArrayGenerator(this);
        }
    }

    private StringArrayGenerator(Builder builder) {
        super(builder.random);
        this.length = builder.length;
        this.elementLength = builder.elementLength;
    }
}
