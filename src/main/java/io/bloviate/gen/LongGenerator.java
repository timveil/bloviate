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

import org.apache.commons.lang3.RandomUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LongGenerator extends AbstractDataGenerator<Long> {

    private final long startInclusive;
    private final long endExclusive;

    @Override
    public Long generate() {
        return RandomUtils.nextLong(startInclusive, endExclusive);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        statement.setLong(parameterIndex, generate());
    }

    public static class Builder {

        private long startInclusive = 0;
        private long endExclusive = Long.MAX_VALUE;

        public Builder start(long start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(long end) {
            this.endExclusive = end;
            return this;
        }

        public LongGenerator build() {
            return new LongGenerator(this);
        }
    }

    private LongGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
