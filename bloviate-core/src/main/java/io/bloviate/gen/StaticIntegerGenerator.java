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
import java.util.random.RandomGenerator;

/**
 * Always generates the same configured integer value. The random source is ignored.
 */
public class StaticIntegerGenerator extends AbstractDataGenerator<Integer> {

    private final Integer value;

    @Override
    public Integer generate() {
        return value;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        statement.setInt(parameterIndex, value);
    }

    @Override
    public Integer get(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    public static class Builder extends AbstractBuilder<Integer> {

        private Integer value = 0;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder value(Integer value) {
            this.value = value;
            return this;
        }

        @Override
        public StaticIntegerGenerator build() {
            return new StaticIntegerGenerator(this);
        }
    }

    private StaticIntegerGenerator(Builder builder) {
        super(builder.random);
        this.value = builder.value;
    }
}
