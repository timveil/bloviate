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

import java.sql.*;
import java.util.random.RandomGenerator;

/**
 * Generator for SQL {@code BLOB} (binary large object) columns.
 *
 * <p>Generation is not yet implemented: {@link #generate()} and {@link #generateAsString()}
 * currently return {@code null}, so nullable {@code BLOB} columns fill with {@code null} and a
 * non-nullable one will fail at insert. {@link #set(Connection, PreparedStatement, int, Blob)}
 * and {@link #get(ResultSet, int)} bind and read a {@link Blob} normally, so round-tripping an
 * existing value works.
 */
public class SqlBlobGenerator extends AbstractDataGenerator<Blob> {
    //todo

    @Override
    public Blob generate() {
        return null;
    }

    @Override
    public String generateAsString() {
        return null;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Blob value) throws SQLException {
        statement.setBlob(parameterIndex, value);
    }

    @Override
    public Blob get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getBlob(columnIndex);
    }

    public static class Builder extends AbstractBuilder<Blob> {

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public SqlBlobGenerator build() {
            return new SqlBlobGenerator(this);
        }
    }

    private SqlBlobGenerator(Builder builder) {
        super(builder.random);

    }
}
