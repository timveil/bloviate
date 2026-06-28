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
import java.util.random.RandomGenerator;

/**
 * Generates a fixed-length {@code String[]} whose elements are random alphabetic strings, bound
 * to JDBC arrays of SQL type {@code VARCHAR}.
 *
 * <p>The array {@link Builder#length(int) length} defaults to {@code 3} and each element's
 * {@link Builder#elementLength(int) elementLength} defaults to {@code 10} characters. Output is
 * seeded and therefore reproducible for a given random source.
 */
public class StringArrayGenerator extends AbstractDataGenerator<String[]> {

    private final int length;
    private final int elementLength;

    @Override
    public String[] generate() {
        String[] randomArray = new String[length];

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
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, String[] value) throws SQLException {
        statement.setArray(parameterIndex, connection.createArrayOf(JDBCType.VARCHAR.getName(), value));
    }

    @Override
    public String[] get(ResultSet resultSet, int columnIndex) throws SQLException {
        Array array = resultSet.getArray(columnIndex);

        if (array == null) {
            return null;
        }

        Object[] elements = (Object[]) array.getArray();
        String[] result = new String[elements.length];

        for (int i = 0; i < elements.length; i++) {
            result[i] = elements[i] != null ? elements[i].toString() : null;
        }

        return result;
    }

    /** Fluent builder for {@link StringArrayGenerator}. */
    public static class Builder extends AbstractBuilder<String[]> {

        private int length = 3;
        private int elementLength = 10;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the number of elements in the generated array. Defaults to {@code 3}.
         *
         * @param length the array length
         * @return this builder, for chaining
         */
        public Builder length(int length) {
            this.length = length;
            return this;
        }

        /**
         * Sets the character length of each generated string element. Defaults to {@code 10}.
         *
         * @param elementLength the number of alphabetic characters per element
         * @return this builder, for chaining
         */
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
