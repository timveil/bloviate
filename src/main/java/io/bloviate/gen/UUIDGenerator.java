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
import java.util.Random;
import java.util.UUID;

/**
 * Generator for UUID (Universally Unique Identifier) values.
 * Produces random UUIDs for database columns that store unique identifiers.
 * The generated UUIDs are deterministic based on the provided random seed.
 *
 * @since 1.0.0
 */
public class UUIDGenerator extends AbstractDataGenerator<UUID> {

    @Override
    public UUID generate() {
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
    public static class Builder extends AbstractBuilder {

        /**
         * Constructs a new Builder for UUIDGenerator.
         *
         * @param random the random number generator to use
         */
        public Builder(Random random) {
            super(random);
        }

        public UUIDGenerator build() {
            return new UUIDGenerator(this);
        }
    }

    private UUIDGenerator(Builder builder) {
        super(builder.random);

    }
}
