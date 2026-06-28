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
 * Generates a single lowercase ASCII letter ({@code 'a'} through {@code 'z'}) chosen uniformly
 * using the seeded random source, so a given seed yields a reproducible sequence. Backed by an
 * {@link IntegerGenerator} over the half-open range {@code [0, 26)} offset from {@code 'a'}.
 */
public class CharacterGenerator extends AbstractDataGenerator<Character> {

    private final IntegerGenerator integerGenerator;


    @Override
    public Character generate() {
        return (char) (integerGenerator.generate() + 'a');
    }

    @Override
    public Character get(ResultSet resultSet, int columnIndex) throws SQLException {
        String character = resultSet.getString(columnIndex);

        if (character != null) {

            if (character.length() > 1) {
                throw new IllegalArgumentException("character length is greater than 1");
            }

            return character.charAt(0);
        }

        return null;
    }

    /** Fluent builder for {@link CharacterGenerator}. */
    public static class Builder extends AbstractBuilder<Character> {
        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public CharacterGenerator build() {
            return new CharacterGenerator(this);
        }
    }

    private CharacterGenerator(Builder builder) {
        super(builder.random);

        this.integerGenerator = new IntegerGenerator.Builder(random).start(0).end(26).build();

    }
}
