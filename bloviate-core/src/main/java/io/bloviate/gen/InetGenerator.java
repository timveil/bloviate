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
 * Generates IPv4 host addresses as a dotted-quad {@link String}, e.g. {@code 12.198.4.251}.
 *
 * <p>Each of the four octets is drawn from {@code 1..255} (inclusive), so the value is a valid
 * PostgreSQL {@code inet} host address. Output is seeded and therefore reproducible for a given
 * random source.
 */
public class InetGenerator extends AbstractDataGenerator<String> {

    private final IntegerGenerator integerGenerator;

    @Override
    public String generate() {
        return integerGenerator.generate() + "." + integerGenerator.generate() + "." + integerGenerator.generate() + "." + integerGenerator.generate();
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Fluent builder for {@link InetGenerator}. */
    public static class Builder extends AbstractBuilder<String> {

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public InetGenerator build() {
            return new InetGenerator(this);
        }
    }

    private InetGenerator(Builder builder) {
        super(builder.random);

        this.integerGenerator = new IntegerGenerator.Builder(random).start(1).end(256).build();

    }
}
