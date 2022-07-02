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

import io.bloviate.util.SeededRandomUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public class DoubleGenerator extends AbstractDataGenerator<Double> {

    private final double startInclusive;
    private final double endExclusive;
    private final Integer maxDigits; // number of places to right of decimal

    @Override
    public Double generate(Random random) {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        double value = randomUtils.nextDouble(startInclusive, endExclusive);

        if (maxDigits != null) {
            value = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        return value;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Double value) throws SQLException {
        statement.setDouble(parameterIndex, value);
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private double startInclusive = 0;
        private Integer maxDigits = null;
        private double endExclusive = Double.MAX_VALUE;

        public Builder start(double start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(double end) {
            this.endExclusive = end;
            return this;
        }

        public Builder maxDigits(Integer maxDigits) {
            this.maxDigits = maxDigits;
            return this;
        }


        @Override
        public DoubleGenerator build() {
            return new DoubleGenerator(this);
        }
    }

    private DoubleGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
        this.maxDigits = builder.maxDigits;
    }
}
