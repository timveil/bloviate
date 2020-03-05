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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SqlTimestampGenerator implements DataGenerator<Timestamp> {

    private final Timestamp startInclusive;
    private final Timestamp endExclusive;

    @Override
    public Timestamp generate() {

        Long randomTime = new LongGenerator.Builder()
                .start(startInclusive.getTime())
                .end(endExclusive.getTime())
                .build().generate();

        return new Timestamp(randomTime);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }


    public static class Builder {

        private Timestamp startInclusive = Timestamp.from(Instant.EPOCH);
        private Timestamp endExclusive = Timestamp.from(Instant.now().plus(100, ChronoUnit.DAYS));

        public Builder start(Timestamp start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Timestamp end) {
            this.endExclusive = end;
            return this;
        }

        public SqlTimestampGenerator build() {
            return new SqlTimestampGenerator(this);
        }
    }

    private SqlTimestampGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
