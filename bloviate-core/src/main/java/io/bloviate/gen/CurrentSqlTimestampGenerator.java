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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.random.RandomGenerator;

/**
 * Generates the current timestamp at the moment of generation. The random
 * source is ignored.
 */
public class CurrentSqlTimestampGenerator extends AbstractDataGenerator<Timestamp> {

    @Override
    public Timestamp generate() {
        return Timestamp.from(Instant.now());
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Timestamp value) throws SQLException {
        statement.setTimestamp(parameterIndex, value);
    }

    @Override
    public Timestamp get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTimestamp(columnIndex);
    }

    public static class Builder extends AbstractBuilder<Timestamp> {

        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public CurrentSqlTimestampGenerator build() {
            return new CurrentSqlTimestampGenerator(this);
        }
    }

    private CurrentSqlTimestampGenerator(Builder builder) {
        super(builder.random);
    }
}
