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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public class SqlTimestampGenerator extends AbstractDataGenerator<Timestamp> {

    private final LongGenerator longGenerator;

    @Override
    public Timestamp generate() {

        Long randomTime = longGenerator.generate();

        return Timestamp.from(Instant.ofEpochMilli(randomTime));
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setTimestamp(parameterIndex, (Timestamp) value);
    }

    @Override
    public Timestamp get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTimestamp(columnIndex);
    }

    public static class Builder extends AbstractBuilder {

        private Timestamp startInclusive = Timestamp.from(Instant.now().minus(100, ChronoUnit.DAYS));
        private Timestamp endExclusive = Timestamp.from(Instant.now().plus(100, ChronoUnit.DAYS));

        public Builder(Random random) {
            super(random);
        }

        public Builder start(Timestamp start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Timestamp end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public SqlTimestampGenerator build() {
            return new SqlTimestampGenerator(this);
        }
    }

    private SqlTimestampGenerator(Builder builder) {
        super(builder.random);

        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.toInstant().toEpochMilli())
                .end(builder.endExclusive.toInstant().toEpochMilli())
                .build();
    }
}
