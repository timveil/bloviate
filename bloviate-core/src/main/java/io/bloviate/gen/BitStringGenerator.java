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
 * Generates a string of {@code '0'} and {@code '1'} characters for SQL {@code BIT} / {@code VARBIT}
 * columns, e.g. {@code 10110}.
 *
 * <p>The number of bits is the configured {@link Builder#size(int) size} clamped to {@code [1, 25]}:
 * {@code BIT}/{@code VARBIT} columns must hold at least one bit, and an unbounded {@code VARBIT}
 * reports a max size of {@code 0} which would otherwise yield an empty (invalid) value. Size
 * defaults to {@code 1}. Output is seeded and therefore reproducible for a given random source.
 */
public class BitStringGenerator extends AbstractDataGenerator<String> {

    private final int size;

    private final BitGenerator bitGenerator;

    @Override
    public String generate() {

        // BIT/VARBIT columns must contain at least one bit; an unbounded VARBIT
        // reports a maxSize of 0, which would otherwise yield an empty (invalid)
        // bit string. Clamp to the range [1, 25].
        int maxSize = Math.clamp(size, 1, 25);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < maxSize; i++) {
            builder.append(bitGenerator.generate());
        }

        return builder.toString();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Fluent builder for {@link BitStringGenerator}. */
    public static class Builder extends AbstractBuilder<String> {
        private int size = 1;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the requested number of bits. The generated length is this value clamped to
         * {@code [1, 25]}. Defaults to {@code 1}.
         *
         * @param size the requested bit-string length
         * @return this builder, for chaining
         */
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        @Override
        public BitStringGenerator build() {
            return new BitStringGenerator(this);
        }
    }

    private BitStringGenerator(Builder builder) {
        super(builder.random);
        this.size = builder.size;
        this.bitGenerator = new BitGenerator.Builder(builder.random).build();
    }
}
