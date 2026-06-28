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
 * Generates small well-formed XML documents, e.g. {@code <bloviate>a1b2c3d4</bloviate>}.
 *
 * <p>The element content is alphanumeric, so the result needs no entity escaping and is
 * accepted by strict {@code xml} columns (e.g. PostgreSQL {@code xml}).
 */
public class XmlGenerator extends AbstractDataGenerator<String> {

    private final SimpleStringGenerator contentGenerator;

    @Override
    public String generate() {
        return "<bloviate>" + contentGenerator.generate() + "</bloviate>";
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Fluent builder for {@link XmlGenerator}. */
    public static class Builder extends AbstractBuilder<String> {

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        @Override
        public XmlGenerator build() {
            return new XmlGenerator(this);
        }
    }

    private XmlGenerator(Builder builder) {
        super(builder.random);
        this.contentGenerator = new SimpleStringGenerator.Builder(random).size(16).letters(true).numbers(true).build();
    }
}
