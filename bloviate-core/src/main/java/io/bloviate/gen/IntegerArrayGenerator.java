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
import java.util.StringJoiner;
import java.util.random.RandomGenerator;

/**
 * Generates a fixed-length {@code Integer[]} whose elements are unbounded random {@code int}
 * values, bound to JDBC arrays of SQL type {@code INTEGER}.
 *
 * <p>The array {@link Builder#length(int) length} defaults to {@code 3}. Output is seeded and
 * therefore reproducible for a given random source.
 */
public class IntegerArrayGenerator extends AbstractDataGenerator<Integer[]> {

    private final int length;

    @Override
    public Integer[] generate() {
        Integer[] randomArray = new Integer[length];

        for (int i = 0; i < length; i++) {
            randomArray[i] = random.nextInt();
        }

        return randomArray;

    }

    /**
     * Renders the generated array as a PostgreSQL array literal — e.g. {@code {1,-42,7}} — so
     * flat-file output (the only consumer of {@code generateAsString}) carries the element values
     * rather than an {@code Integer[]} identity string.
     */
    @Override
    public String generateAsString() {
        StringJoiner joiner = new StringJoiner(",", "{", "}");

        for (Integer element : generate()) {
            joiner.add(element == null ? "NULL" : element.toString());
        }

        return joiner.toString();
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer[] value) throws SQLException {
        statement.setArray(parameterIndex, connection.createArrayOf(JDBCType.INTEGER.getName(), value));
    }

    @Override
    public Integer[] get(ResultSet resultSet, int columnIndex) throws SQLException {
        Array array = resultSet.getArray(columnIndex);

        if (array == null) {
            return null;
        }

        Object[] elements = (Object[]) array.getArray();
        Integer[] result = new Integer[elements.length];

        for (int i = 0; i < elements.length; i++) {
            result[i] = elements[i] != null ? ((Number) elements[i]).intValue() : null;
        }

        return result;
    }

    /** Fluent builder for {@link IntegerArrayGenerator}. */
    public static class Builder extends AbstractBuilder<Integer[]> {

        private int length = 3;

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

        @Override
        public IntegerArrayGenerator build() {
            return new IntegerArrayGenerator(this);
        }
    }

    private IntegerArrayGenerator(Builder builder) {
        super(builder.random);
        this.length = builder.length;
    }
}
