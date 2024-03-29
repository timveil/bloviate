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

public class LongGenerator extends AbstractDataGenerator<Long> {

    private final long startInclusive;
    private final long endExclusive;

    @Override
    public Long generate() {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        return randomUtils.nextLong(startInclusive, endExclusive);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setLong(parameterIndex, (Long) value);
    }

    @Override
    public Long get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getLong(columnIndex);
    }

    public static class Builder extends AbstractBuilder {

        private long startInclusive = 0;
        private long endExclusive = Long.MAX_VALUE;

        public Builder(Random random) {
            super(random);
        }

        public Builder start(long start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(long end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public LongGenerator build() {
            return new LongGenerator(this);
        }
    }

    private LongGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
