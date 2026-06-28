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
 * Generates IPv4 CIDR network specifications of the form {@code a.b.c.0/24}.
 *
 * <p>The host portion is always zero (a {@code /24} network), so the value is a valid
 * PostgreSQL {@code cidr} where all bits to the right of the network mask must be zero.
 */
public class CidrGenerator extends AbstractDataGenerator<String> {

    private final IntegerGenerator octetGenerator;

    @Override
    public String generate() {
        return octetGenerator.generate() + "." + octetGenerator.generate() + "." + octetGenerator.generate() + ".0/24";
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Fluent builder for {@link CidrGenerator}. */
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
        public CidrGenerator build() {
            return new CidrGenerator(this);
        }
    }

    private CidrGenerator(Builder builder) {
        super(builder.random);
        this.octetGenerator = new IntegerGenerator.Builder(random).start(1).end(256).build();
    }
}
