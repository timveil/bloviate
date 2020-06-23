/*
 * Copyright 2020 Tim Veil
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class ByteGenerator extends AbstractDataGenerator<Byte[]> {

    private final int size;

    @Override
    public Byte[] generate() {

        int maxSize = Math.min(size, 25);

        return ArrayUtils.toObject(RandomUtils.nextBytes(maxSize));
    }

    @Override
    public String generateAsString() {
        return Arrays.toString(RandomUtils.nextBytes(size));
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setBytes(parameterIndex, ArrayUtils.toPrimitive((Byte[]) value));
    }

    @Override
    public Byte[] get(ResultSet resultSet, int columnIndex) throws SQLException {
        return ArrayUtils.toObject(resultSet.getBytes(columnIndex));
    }

    public static class Builder {

        private int size = 25;

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public ByteGenerator build() {
            return new ByteGenerator(this);
        }
    }

    private ByteGenerator(Builder builder) {
        this.size = builder.size;
    }
}
