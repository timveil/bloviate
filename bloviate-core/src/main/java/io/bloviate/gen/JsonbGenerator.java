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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;

/**
 * Generator for JSON / JSONB columns. Produces a JSON object literal (as a {@link String}) with a
 * configurable number of randomly named fields whose values cycle through the common JSON scalar
 * types (string, integer, double, boolean). Generation is deterministic for a given seed.
 *
 * <p>The value is emitted as a {@code String}; databases that accept a textual JSON representation
 * (e.g. PostgreSQL / CockroachDB {@code jsonb}) consume it via the default {@code setObject}
 * binding, mirroring the other textual generators ({@code InetGenerator}, {@code IntervalGenerator}).
 */
public class JsonbGenerator extends AbstractDataGenerator<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int fields;

    @Override
    public String generate() {
        ObjectNode node = MAPPER.createObjectNode();

        for (int i = 0; i < fields; i++) {
            String key = randomUtils.randomAlphabetic(8);

            switch (i % 4) {
                case 0 -> node.put(key, randomUtils.randomAlphabetic(12));
                case 1 -> node.put(key, randomUtils.nextInt(0, Integer.MAX_VALUE));
                case 2 -> node.put(key, randomUtils.nextDouble(0, Double.MAX_VALUE));
                default -> node.put(key, random.nextBoolean());
            }
        }

        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // ObjectNode built here is always serializable; treat any failure as fatal
            throw new IllegalStateException("unable to serialize generated JSON", e);
        }
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    public static class Builder extends AbstractBuilder<String> {

        private int fields = 3;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder fields(int fields) {
            this.fields = fields;
            return this;
        }

        @Override
        public JsonbGenerator build() {
            return new JsonbGenerator(this);
        }
    }

    private JsonbGenerator(Builder builder) {
        super(builder.random);
        this.fields = builder.fields;
    }
}
