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
 * Generates a single bit as an {@code Integer} that is either {@code 0} or {@code 1}, chosen
 * uniformly using the seeded random source (so a given seed yields a reproducible sequence).
 * Backed by an {@link IntegerGenerator} over the half-open range {@code [0, 2)}. Suitable for
 * {@code BIT} columns.
 */
public class BitGenerator extends AbstractDataGenerator<Integer> {

    private final IntegerGenerator integerGenerator;

    @Override
    public Integer generate() {
        return integerGenerator.generate();
    }

    @Override
    public Integer get(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    /** Fluent builder for {@link BitGenerator}. */
    public static class Builder extends AbstractBuilder<Integer> {

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public BitGenerator build() {
            return new BitGenerator(this);
        }
    }

    private BitGenerator(Builder builder) {
        super(builder.random);

        this.integerGenerator = new IntegerGenerator.Builder(builder.random).start(0).end(2).build();
    }
}
