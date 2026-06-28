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
 * Generates fixed-length random strings. Every value is exactly {@code size} characters long
 * (capped at 2000 at generation time), drawn from a configurable character set: ASCII letters
 * ({@code A-Za-z}), decimal digits ({@code 0-9}), or both. Backed by the builder's seeded
 * {@link java.util.random.RandomGenerator}, so the same seed yields identical output.
 */
public class SimpleStringGenerator extends AbstractDataGenerator<String> {

    private final int size;
    private final boolean letters;
    private final boolean numbers;

    @Override
    public String generate() {
        int maxSize = Math.min(size, 2000);
        return randomUtils.random(maxSize, letters, numbers);
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /**
     * Builder for {@link SimpleStringGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<String> {

        private int size = 10;

        private boolean letters = true;

        private boolean numbers = false;

        /**
         * Constructs a new builder.
         *
         * @param random the seeded random generator backing the produced generator
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the fixed string length.
         *
         * @param size the number of characters in each generated string; capped at 2000 at
         *             generation time. Defaults to {@code 10}.
         * @return this builder, for chaining
         */
        public Builder size(int size) {
            this.size = size;
            return this;
        }

        /**
         * Controls whether ASCII letters ({@code A-Za-z}) are included in the character set.
         *
         * @param letters {@code true} to include letters. Defaults to {@code true}.
         * @return this builder, for chaining
         */
        public Builder letters(boolean letters) {
            this.letters = letters;
            return this;
        }

        /**
         * Controls whether decimal digits ({@code 0-9}) are included in the character set.
         *
         * @param numbers {@code true} to include digits. Defaults to {@code false}.
         * @return this builder, for chaining
         */
        public Builder numbers(boolean numbers) {
            this.numbers = numbers;
            return this;
        }

        @Override
        public SimpleStringGenerator build() {
            return new SimpleStringGenerator(this);
        }
    }

    private SimpleStringGenerator(Builder builder) {
        super(builder.random);
        this.size = builder.size;
        this.letters = builder.letters;
        this.numbers = builder.numbers;
    }
}
