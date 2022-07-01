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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public class InstantGenerator extends AbstractDataGenerator<Instant> {

    private final LongGenerator longGenerator;

    @Override
    public Instant generate(Random random) {

        Long randomTime = longGenerator.generate(random);

        return Instant.ofEpochMilli(randomTime);
    }

    public static class Builder implements io.bloviate.gen.Builder {

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

        @Override
        public InstantGenerator build() {
            return new InstantGenerator(this);
        }
    }

    private InstantGenerator(Builder builder) {

        this.longGenerator = new LongGenerator.Builder()
                .start(builder.startInclusive.toEpochMilli())
                .end(builder.endExclusive.toEpochMilli())
                .build();
    }
}
