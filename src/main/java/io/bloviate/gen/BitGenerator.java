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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BitGenerator extends AbstractDataGenerator<String> {

    private final int size;

    @Override
    public String generate() {

        int maxSize = Math.min(size, 25);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < maxSize; i++) {
            builder.append(RandomUtils.nextInt(0, 2));
        }

        return builder.toString();
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    public static class Builder {
        private int size = 1;

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public BitGenerator build() {
            return new BitGenerator(this);
        }
    }

    private BitGenerator(Builder builder) {
        this.size = builder.size;
    }
}
