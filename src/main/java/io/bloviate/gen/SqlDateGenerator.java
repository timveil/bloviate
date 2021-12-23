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

public class SqlDateGenerator extends AbstractDataGenerator<Date> {

    private final LongGenerator longGenerator;

    @Override
    public Date generate() {

        Long randomTime = longGenerator.generate();

        return new Date(randomTime);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setDate(parameterIndex, (Date) value);
    }

    @Override
    public Date get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getDate(columnIndex);
    }

    public static class Builder extends AbstractBuilder {

        private Date startInclusive = new Date(Instant.now().minus(100, ChronoUnit.DAYS).toEpochMilli());
        private Date endExclusive = new Date(Instant.now().plus(100, ChronoUnit.DAYS).toEpochMilli());

        public Builder(Random random) {
            super(random);
        }

        public Builder start(Date start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Date end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public SqlDateGenerator build() {
            return new SqlDateGenerator(this);
        }
    }

    private SqlDateGenerator(Builder builder) {
        super(builder.random);
        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
