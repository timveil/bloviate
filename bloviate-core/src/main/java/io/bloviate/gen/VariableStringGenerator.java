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
 * Generates strings whose length varies randomly within an inclusive
 * {@code [minLength, maxLength]} range. The character set (letters and/or
 * numbers) is configurable.
 */
public class VariableStringGenerator extends AbstractDataGenerator<String> {

    private final int minLength;
    private final int maxLength;
    private final boolean letters;
    private final boolean numbers;

    @Override
    public String generate() {
        int length = randomUtils.nextInt(Math.max(minLength, 1), maxLength + 1);
        return randomUtils.random(length, letters, numbers);
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /**
     * Builder for {@link VariableStringGenerator} instances.
     */
    public static class Builder extends AbstractBuilder<String> {

        private int minLength = 1;
        private int maxLength = 10;
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
         * Sets the inclusive lower bound of the random length range.
         *
         * @param minLength the minimum string length; effectively clamped up to {@code 1} at
         *                  generation time (lengths below 1 are treated as 1). Defaults to {@code 1}.
         * @return this builder, for chaining
         */
        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        /**
         * Sets the inclusive upper bound of the random length range.
         *
         * @param maxLength the maximum string length, inclusive. Defaults to {@code 10}.
         * @return this builder, for chaining
         */
        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
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
        public VariableStringGenerator build() {
            return new VariableStringGenerator(this);
        }
    }

    private VariableStringGenerator(Builder builder) {
        super(builder.random);
        this.minLength = builder.minLength;
        this.maxLength = builder.maxLength;
        this.letters = builder.letters;
        this.numbers = builder.numbers;
    }
}
