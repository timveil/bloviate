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
import java.util.random.RandomGenerator;

/**
 * Always generates the same configured string value. The random source is ignored.
 */
public class StaticStringGenerator extends AbstractDataGenerator<String> {

    private final String value;

    @Override
    public String generate() {
        return value;
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder<String> {

        private String value = "";

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        public StaticStringGenerator build() {
            return new StaticStringGenerator(this);
        }
    }

    private StaticStringGenerator(Builder builder) {
        super(builder.random);
        this.value = builder.value;
    }
}
