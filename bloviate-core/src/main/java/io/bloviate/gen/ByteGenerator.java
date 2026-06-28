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

import org.apache.commons.lang3.ArrayUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Generates an array of random {@code Byte}s using the seeded random source, so a given seed
 * yields a reproducible sequence. The array length is the configured {@code size} capped at
 * {@code 25} bytes; the default size is {@code 25}. Suitable for {@code BINARY}/{@code VARBINARY}
 * style columns.
 */
public class ByteGenerator extends AbstractDataGenerator<Byte[]> {

    private final int size;

    @Override
    public Byte[] generate() {

        int maxSize = Math.min(size, 25);

        final byte[] result = new byte[maxSize];
        random.nextBytes(result);

        return ArrayUtils.toObject(result);
    }

    @Override
    public String generateAsString() {
        return Arrays.toString(generate());
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Byte[] value) throws SQLException {
        statement.setBytes(parameterIndex, ArrayUtils.toPrimitive(value));
    }

    @Override
    public Byte[] get(ResultSet resultSet, int columnIndex) throws SQLException {
        return ArrayUtils.toObject(resultSet.getBytes(columnIndex));
    }

    /** Fluent builder for {@link ByteGenerator}. */
    public static class Builder extends AbstractBuilder<Byte[]> {

        private int size = 25;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the requested number of bytes to generate. Defaults to {@code 25}. The effective
         * length is capped at {@code 25} bytes at generation time, so larger values produce a
         * 25-byte array.
         *
         * @param size the requested array length in bytes
         * @return this builder, for chaining
         */
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        @Override
        public ByteGenerator build() {
            return new ByteGenerator(this);
        }
    }

    private ByteGenerator(Builder builder) {
        super(builder.random);
        this.size = builder.size;
    }
}
