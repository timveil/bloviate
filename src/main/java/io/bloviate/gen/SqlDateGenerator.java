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


import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SqlDateGenerator extends AbstractDataGenerator<Date> {

    private final LongGenerator longGenerator;

    @Override
    public Date generate() {

        Long randomTime = longGenerator.generate();

        return new Date(randomTime);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        statement.setDate(parameterIndex, generate());
    }

    public static class Builder {

        private Date startInclusive = new Date(Instant.EPOCH.toEpochMilli());
        private Date endExclusive = new Date(Instant.now().plus(100, ChronoUnit.HOURS).toEpochMilli());

        public Builder start(Date start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Date end) {
            this.endExclusive = end;
            return this;
        }

        public SqlDateGenerator build() {
            return new SqlDateGenerator(this);
        }
    }

    private SqlDateGenerator(Builder builder) {
        this.longGenerator = new LongGenerator.Builder()
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
