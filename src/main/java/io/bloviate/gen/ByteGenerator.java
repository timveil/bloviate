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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.Arrays;

public class ByteGenerator implements DataGenerator<Byte[]> {

    private final int size;

    @Override
    public Byte[] generate() {

        int maxLength = Math.min(size, 25);

        return ArrayUtils.toObject(RandomUtils.nextBytes(maxLength));
    }

    @Override
    public String generateAsString() {
        return Arrays.toString(RandomUtils.nextBytes(size));
    }

    public int getSize() {
        return size;
    }

    public static class Builder {

        private int size = 25;

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public ByteGenerator build() {
            return new ByteGenerator(this);
        }
    }

    private ByteGenerator(Builder builder) {
        this.size = builder.size;
    }
}
