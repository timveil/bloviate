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

public class IntegerGenerator implements DataGenerator<Integer> {

    private final int startInclusive;
    private final int endExclusive;

    @Override
    public Integer generate() {
        return RandomUtils.nextInt(startInclusive, endExclusive);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        private int startInclusive = 0;
        private int endExclusive = Integer.MAX_VALUE;

        public Builder start(int start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(int end) {
            this.endExclusive = end;
            return this;
        }

        public IntegerGenerator build() {
            return new IntegerGenerator(this);
        }
    }

    private IntegerGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
