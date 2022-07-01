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

public class InetGenerator extends AbstractDataGenerator<String> {

    private final IntegerGenerator integerGenerator;

    @Override
    public String generate(Random random) {
        return integerGenerator.generate(random) + "." + integerGenerator.generate(random) + "." + integerGenerator.generate(random) + "." + integerGenerator.generate(random);
    }

    @Override
    public String generateAsString(Random random) {
        return generate(random);
    }

    public static class Builder implements io.bloviate.gen.Builder {

        @Override
        public InetGenerator build() {
            return new InetGenerator(this);
        }
    }

    private InetGenerator(Builder builder) {
        this.integerGenerator = new IntegerGenerator.Builder().start(1).end(256).build();

    }
}
