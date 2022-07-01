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

import java.util.Random;

public class CharacterGenerator extends AbstractDataGenerator<Character> {

    private final IntegerGenerator integerGenerator;


    @Override
    public Character generate(Random random) {
        return (char) (integerGenerator.generate(random) + 'a');
    }

    public static class Builder implements io.bloviate.gen.Builder {
        @Override
        public CharacterGenerator build() {
            return new CharacterGenerator(this);
        }
    }

    private CharacterGenerator(Builder builder) {
        this.integerGenerator = new IntegerGenerator.Builder().start(0).end(26).build();

    }
}
