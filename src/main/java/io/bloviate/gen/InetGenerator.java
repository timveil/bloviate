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

public class InetGenerator implements DataGenerator<String> {

    @Override
    public String generate() {
        return RandomUtils.nextInt(1, 256) + "." + RandomUtils.nextInt(1, 256) + "." + RandomUtils.nextInt(1, 256) + "." + RandomUtils.nextInt(1, 256);
    }

    @Override
    public String generateAsString() {
        return generate();
    }


    public static class Builder {

        public InetGenerator build() {
            return new InetGenerator(this);
        }
    }

    private InetGenerator(Builder builder) {

    }
}