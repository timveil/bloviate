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

import org.apache.commons.lang3.RandomStringUtils;

public class StringArrayGenerator implements DataGenerator<String[]> {

    private final int length;
    private final int elementLength;
    private final DataGenerator<? extends String> elementGenerator;

    @Override
    public String[] generate() {
        String[] random = new String[length];

        for (int i = 0; i < length; i++) {
            if (elementGenerator != null) {
                random[i] = elementGenerator.generate();
            } else {
                random[i] = RandomStringUtils.randomAlphabetic(elementLength);
            }
        }

        return random;

    }

    @Override
    public String generateAsString() {
        return null;
    }

    public static class Builder {

        private int length = 3;
        private int elementLength = 10;
        private DataGenerator<? extends String> elementGenerator;

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder elementLength(int elementLength) {
            this.elementLength = elementLength;
            return this;
        }

        public Builder elementGenerator(DataGenerator<? extends String> generator) {
            this.elementGenerator = generator;
            return this;
        }

        public StringArrayGenerator build() {
            return new StringArrayGenerator(this);
        }
    }

    private StringArrayGenerator(Builder builder) {
        this.length = builder.length;
        this.elementLength = builder.elementLength;
        this.elementGenerator = builder.elementGenerator;
    }
}
