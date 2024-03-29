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

    public static class Builder extends AbstractBuilder {

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
