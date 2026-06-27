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
 * Generates MAC addresses as colon-separated hex octets, e.g. {@code 08:00:2b:01:02:03}.
 *
 * <p>Defaults to six octets (PostgreSQL {@code macaddr}); set {@link Builder#octets(int)}
 * to {@code 8} for the EUI-64 {@code macaddr8} form.
 */
public class MacAddressGenerator extends AbstractDataGenerator<String> {

    private final int octets;
    private final IntegerGenerator octetGenerator;

    @Override
    public String generate() {
        StringBuilder builder = new StringBuilder(octets * 3);
        for (int i = 0; i < octets; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(String.format("%02x", octetGenerator.generate()));
        }
        return builder.toString();
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder<String> {

        private int octets = 6;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder octets(int octets) {
            this.octets = octets;
            return this;
        }

        @Override
        public MacAddressGenerator build() {
            return new MacAddressGenerator(this);
        }
    }

    private MacAddressGenerator(Builder builder) {
        super(builder.random);
        this.octets = builder.octets;
        this.octetGenerator = new IntegerGenerator.Builder(random).start(0).end(256).build();
    }
}
