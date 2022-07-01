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

public class SqlTimeGenerator extends AbstractDataGenerator<Time> {

    private final LongGenerator longGenerator;

    @Override
    public Time generate(Random random) {

        Long randomTime = longGenerator.generate(random);

        return new Time(randomTime);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Time value) throws SQLException {
        statement.setTime(parameterIndex, value);
    }

    @Override
    public Time get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getTime(columnIndex);
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private Time startInclusive = new Time(Instant.EPOCH.toEpochMilli());
        private Time endExclusive = new Time(Instant.now().plus(100, ChronoUnit.HOURS).toEpochMilli());

        public Builder start(Time start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Time end) {
            this.endExclusive = end;
            return this;
        }

        @Override
        public SqlTimeGenerator build() {
            return new SqlTimeGenerator(this);
        }
    }

    private SqlTimeGenerator(Builder builder) {

        this.longGenerator = new LongGenerator.Builder()
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
