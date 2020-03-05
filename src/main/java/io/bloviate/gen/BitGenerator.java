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

public class BitGenerator implements DataGenerator<String> {

    private final int length;

    @Override
    public String generate() {

        int maxLength = Math.min(length, 25);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < maxLength; i++) {
            builder.append(RandomUtils.nextInt(0, 2));
        }

        return builder.toString();
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    public static class Builder {
        private int length = 1;

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public BitGenerator build() {
            return new BitGenerator(this);
        }
    }

    private BitGenerator(Builder builder) {
        this.length = builder.length;
    }
}
