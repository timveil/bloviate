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

import io.bloviate.util.SeededRandomUtils;

import java.util.Random;

public class VariableStringGenerator extends AbstractDataGenerator<String> {

    private final int minSize;
    private final int maxSize;
    private final boolean letters;
    private final boolean numbers;

    @Override
    public String generate(Random random) {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        return randomUtils.random(randomUtils.nextInt(Math.max(minSize, 1), maxSize), letters, numbers);
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private int minSize = 1;
        private int maxSize = 10;
        private boolean letters = true;
        private boolean numbers = false;

        public Builder end(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder start(int minSize) {
            this.minSize = minSize;
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

        @Override
        public VariableStringGenerator build() {
            return new VariableStringGenerator(this);
        }
    }

    private VariableStringGenerator(Builder builder) {
        this.minSize = builder.minSize;
        this.maxSize = builder.maxSize;
        this.letters = builder.letters;
        this.numbers = builder.numbers;
    }
}
