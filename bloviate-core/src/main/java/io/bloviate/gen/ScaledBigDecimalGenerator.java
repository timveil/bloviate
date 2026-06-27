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
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

/**
 * Generates a {@link BigDecimal} drawn uniformly from the half-open range
 * {@code [startInclusive, endExclusive)} and rounded (HALF_UP) to a fixed
 * {@code scale}. Useful for columns that require values within a specific
 * numeric range and decimal scale rather than merely within the column's
 * declared width (e.g. a tax rate constrained to {@code [0.0000, 0.2000]}).
 */
public class ScaledBigDecimalGenerator extends AbstractDataGenerator<BigDecimal> {

    private final double startInclusive;
    private final double endExclusive;
    private final int scale;

    @Override
    public BigDecimal generate() {
        double value = randomUtils.nextDouble(startInclusive, endExclusive);
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, BigDecimal value) throws SQLException {
        statement.setBigDecimal(parameterIndex, value);
    }

    @Override
    public BigDecimal get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getBigDecimal(columnIndex);
    }

    public static class Builder extends AbstractBuilder<BigDecimal> {

        private double startInclusive = 0;
        private double endExclusive = 1;
        private int scale = 2;

        public Builder(Random random) {
            super(random);
        }

        public Builder start(double start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(double end) {
            this.endExclusive = end;
            return this;
        }

        public Builder scale(int scale) {
            this.scale = scale;
            return this;
        }

        @Override
        public ScaledBigDecimalGenerator build() {
            return new ScaledBigDecimalGenerator(this);
        }
    }

    private ScaledBigDecimalGenerator(Builder builder) {
        super(builder.random);
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
        this.scale = builder.scale;
    }
}
