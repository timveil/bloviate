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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;

/**
 * Always generates the same configured {@link BigDecimal} value. The random source is ignored.
 */
public class StaticBigDecimalGenerator extends AbstractDataGenerator<BigDecimal> {

    private final BigDecimal value;

    @Override
    public BigDecimal generate() {
        return value;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, BigDecimal value) throws SQLException {
        statement.setBigDecimal(parameterIndex, value);
    }

    @Override
    public BigDecimal get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getBigDecimal(columnIndex);
    }

    /** Fluent builder for {@link StaticBigDecimalGenerator}. */
    public static class Builder extends AbstractBuilder<BigDecimal> {

        private BigDecimal value = BigDecimal.ZERO;

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
         * Sets the fixed value returned by every call to {@code generate()}. Defaults to
         * {@code BigDecimal.ZERO}.
         *
         * @param value the constant value to generate
         * @return this builder, for chaining
         */
        public Builder value(BigDecimal value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the fixed value returned by every call to {@code generate()}, parsed from the given
         * string via the {@link BigDecimal#BigDecimal(String)} constructor.
         *
         * @param value the constant value to generate, as a {@code BigDecimal} string representation
         * @return this builder, for chaining
         */
        public Builder value(String value) {
            this.value = new BigDecimal(value);
            return this;
        }

        @Override
        public StaticBigDecimalGenerator build() {
            return new StaticBigDecimalGenerator(this);
        }
    }

    private StaticBigDecimalGenerator(Builder builder) {
        super(builder.random);
        this.value = builder.value;
    }
}
