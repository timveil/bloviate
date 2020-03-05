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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class InstantGenerator implements DataGenerator<Instant> {

    private final Instant startInclusive;
    private final Instant endExclusive;

    @Override
    public Instant generate() {

        Long randomTime = new LongGenerator.Builder()
                .start(startInclusive.toEpochMilli())
                .end(endExclusive.toEpochMilli())
                .build().generate();

        return Instant.ofEpochMilli(randomTime);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }


    public static class Builder {

        private Instant startInclusive = Instant.EPOCH;
        private Instant endExclusive = Instant.now().plus(100, ChronoUnit.DAYS);

        public Builder start(Instant start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Instant end) {
            this.endExclusive = end;
            return this;
        }

        public InstantGenerator build() {
            return new InstantGenerator(this);
        }
    }

    private InstantGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
