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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public class BitStringGenerator extends AbstractDataGenerator<String> {

    private final int size;

    private final BitGenerator bitGenerator;

    @Override
    public String generate() {

        int maxSize = Math.min(size, 25);

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < maxSize; i++) {
            builder.append(bitGenerator.generate());
        }

        return builder.toString();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder {
        private int size = 1;

        public Builder(Random random) {
            super(random);
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        @Override
        public BitStringGenerator build() {
            return new BitStringGenerator(this);
        }
    }

    private BitStringGenerator(Builder builder) {
        super(builder.random);
        this.size = builder.size;
        this.bitGenerator = new BitGenerator.Builder(builder.random).build();
    }
}
