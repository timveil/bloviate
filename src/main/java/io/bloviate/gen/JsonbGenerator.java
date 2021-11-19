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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public class JsonbGenerator extends AbstractDataGenerator<String> {
    // todo

    @Override
    public String generate() {
        return null;
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return null;
    }

    public static class Builder extends AbstractBuilder {

        public Builder(Random random) {
            super(random);
        }

        @Override
        public JsonbGenerator build() {
            return new JsonbGenerator(this);
        }
    }

    private JsonbGenerator(Builder builder) {
        super(builder.random);

    }
}
