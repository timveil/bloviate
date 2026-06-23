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

import io.bloviate.util.SeededRandomUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

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
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        int length = randomUtils.nextInt(Math.max(minLength, 1), maxLength + 1);
        return randomUtils.random(length, letters, numbers);
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder<String> {

        private int minLength = 1;
        private int maxLength = 10;
        private boolean letters = true;
        private boolean numbers = false;

        public Builder(Random random) {
            super(random);
        }

        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder letters(boolean letters) {
            this.letters = letters;
            return this;
        }

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
