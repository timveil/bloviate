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
 * Produces version-4-shaped UUIDs built from two seeded pseudo-random longs (with the
 * version and IETF-variant bits set), so the generated UUIDs are deterministic for a given
 * random seed. They are dummy data — no uniqueness or unguessability guarantee is made.
 *
 * @since 1.0.0
 */
public class UUIDGenerator extends AbstractDataGenerator<UUID> {

    @Override
    public UUID generate() {
        // Within a version, the same seed must always yield the same UUIDs. Changing this
        // derivation is allowed in a new release, but it changes every generated UUID for a
        // given seed — make it a deliberate, release-noted change and regenerate the
        // SeedGoldenDumpTest golden file. (Releases <= 2.18.6 derived version-3 UUIDs via an
        // MD5 digest per value; this direct v4 construction was such a release-noted change.)
        long msb = (random.nextLong() & 0xFFFF_FFFF_FFFF_0FFFL) | 0x0000_0000_0000_4000L;
        long lsb = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
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
