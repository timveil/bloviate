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

import java.sql.Array;

public class SqlArrayGenerator implements DataGenerator<Array> {

    private final SqlArrayType arrayType;
    private final int size;

    @Override
    public Array generate() {
        // todo: lots of work here
        if (arrayType.equals(SqlArrayType.STRING)) {
            String[] random = new String[10];

            for (int i =0; i < size; i++) {
                random[i] = RandomStringUtils.random(10);
            }

            //return Connection.create;
        }
        return null;
    }

    @Override
    public String generateAsString() {
        return null;
    }

    public static class Builder {

        private SqlArrayType arrayType;

        private int size = 10;

        public Builder type(SqlArrayType arrayType) {
            this.arrayType = arrayType;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public SqlArrayGenerator build() {
            return new SqlArrayGenerator(this);
        }
    }

    private SqlArrayGenerator(Builder builder) {
        this.arrayType = builder.arrayType;
        this.size = builder.size;
    }
}
