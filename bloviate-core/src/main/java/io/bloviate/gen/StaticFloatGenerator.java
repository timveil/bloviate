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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;

/**
 * Always generates the same configured float value. The random source is ignored.
 */
public class StaticFloatGenerator extends AbstractDataGenerator<Float> {

    private final Float value;

    @Override
    public Float generate() {
        return value;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Float value) throws SQLException {
        statement.setFloat(parameterIndex, value);
    }

    @Override
    public Float get(ResultSet resultSet, int columnIndex) throws SQLException {
        float value = resultSet.getFloat(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    /** Fluent builder for {@link StaticFloatGenerator}. */
    public static class Builder extends AbstractBuilder<Float> {

        private Float value = 0f;

        /**
         * Creates a builder backed by the given random source. The source is never used, since this
         * generator always returns the configured value.
         *
         * @param random the (unused) random source
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the fixed value returned by every call to {@code generate()}. Defaults to {@code 0}.
         *
         * @param value the constant value to generate
         * @return this builder, for chaining
         */
        public Builder value(Float value) {
            this.value = value;
            return this;
        }

        @Override
        public StaticFloatGenerator build() {
            return new StaticFloatGenerator(this);
        }
    }

    private StaticFloatGenerator(Builder builder) {
        super(builder.random);
        this.value = builder.value;
    }
}
