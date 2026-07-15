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
import java.util.UUID;

/**
 * Generator for UUID (Universally Unique Identifier) values.
 * Produces version-3 (name-based, per {@link UUID#nameUUIDFromBytes}) UUIDs derived from
 * 16 seeded pseudo-random bytes, so the generated UUIDs are deterministic for a given
 * random seed. They are dummy data — not version-4 random UUIDs, and no uniqueness or
 * unguessability guarantee is made.
 *
 * @since 1.0.0
 */
public class UUIDGenerator extends AbstractDataGenerator<UUID> {

    @Override
    public UUID generate() {
        // Seed reproducibility is a hard product guarantee: the same schema and seed must yield
        // identical data across releases. Changing this derivation (e.g. to a cheaper v4
        // construction from two nextLong() draws) changes every generated UUID for a given seed
        // and is therefore a breaking change — do not alter it for performance.
        byte[] array = new byte[16];
        random.nextBytes(array);
        return UUID.nameUUIDFromBytes(array);
    }

    @Override
    public UUID get(ResultSet resultSet, int columnIndex) throws SQLException {
        String string = resultSet.getString(columnIndex);

        if (string != null) {
            return UUID.fromString(string);
        }

        return null;
    }

    /**
     * Builder for creating UUIDGenerator instances.
     */
    public static class Builder extends AbstractBuilder<UUID> {

        /**
         * Constructs a new Builder for UUIDGenerator.
         *
         * @param random the random number generator to use
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public UUIDGenerator build() {
            return new UUIDGenerator(this);
        }
    }

    private UUIDGenerator(Builder builder) {
        super(builder.random);

    }
}
