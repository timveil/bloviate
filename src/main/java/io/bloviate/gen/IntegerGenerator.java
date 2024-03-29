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

import io.bloviate.util.SeededRandomUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public class IntegerGenerator extends AbstractDataGenerator<Integer> {

    private final int startInclusive;
    private final int endExclusive;

    @Override
    public Integer generate() {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        return randomUtils.nextInt(startInclusive, endExclusive);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setInt(parameterIndex, (Integer) value);
    }

    @Override
    public Integer get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getInt(columnIndex);
    }

    public static class Builder extends AbstractBuilder {

        private int startInclusive = 0;
        private int endExclusive = Integer.MAX_VALUE;

        public Builder(Random random) {
            super(random);
        }

        public Builder start(int start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(int end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public IntegerGenerator build() {
            return new IntegerGenerator(this);
        }
    }

    private IntegerGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
