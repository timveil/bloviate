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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;

public class DateGenerator extends AbstractDataGenerator<Date> {


    private final LongGenerator longGenerator;

    @Override
    public Date generate() {

        Long randomTime = longGenerator.generate();

        return new Date(randomTime);
    }

    @Override
    public Date get(ResultSet resultSet, int columnIndex) throws SQLException {
        return null;
    }

    public static class Builder extends AbstractBuilder {

        private Date startInclusive = Date.from(Instant.EPOCH);
        private Date endExclusive = Date.from(Instant.now().plus(100, ChronoUnit.DAYS));

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
        public DateGenerator build() {
            return new DateGenerator(this);
        }
    }

    private DateGenerator(Builder builder) {
        super(builder.random);

        this.longGenerator = new LongGenerator.Builder(builder.random)
                .start(builder.startInclusive.getTime())
                .end(builder.endExclusive.getTime())
                .build();
    }
}
