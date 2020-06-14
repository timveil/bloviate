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

import org.apache.commons.lang3.RandomUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DoubleGenerator extends AbstractDataGenerator<Double> {

    private final double startInclusive;
    private final double endExclusive;

    @Override
    public Double generate() {
        return RandomUtils.nextDouble(startInclusive, endExclusive);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        statement.setDouble(parameterIndex, generate());
    }

    public static class Builder {

        private double startInclusive = 0;
        private double endExclusive = Double.MAX_VALUE;

        public Builder start(double start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(double end) {
            this.endExclusive = end;
            return this;
        }

        public DoubleGenerator build() {
            return new DoubleGenerator(this);
        }
    }

    private DoubleGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
