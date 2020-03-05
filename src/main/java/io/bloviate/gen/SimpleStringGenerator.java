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

public class SimpleStringGenerator implements DataGenerator<String> {

    private final int length;
    private final boolean letters;
    private final boolean numbers;

    @Override
    public String generate() {
        int maxLength = Math.min(length, 2000);
        return RandomStringUtils.random(maxLength, letters, numbers);
    }

    @Override
    public String generateAsString() {
        return generate();
    }


    public static class Builder {

        private int length = 10;

        private boolean letters = true;

        private boolean numbers = false;

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder letters(boolean letters) {
            this.letters = letters;
            return this;
        }

        public Builder numbers(boolean numbers) {
            this.numbers = letters;
            return this;
        }

        public SimpleStringGenerator build() {
            return new SimpleStringGenerator(this);
        }
    }

    private SimpleStringGenerator(Builder builder) {
        this.length = builder.length;
        this.letters = builder.letters;
        this.numbers = builder.numbers;
    }
}
