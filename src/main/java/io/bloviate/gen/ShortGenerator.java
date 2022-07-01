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
import java.util.Random;

public class ShortGenerator extends AbstractDataGenerator<Short> {

    private final IntegerGenerator integerGenerator;

    @Override
    public Short generate(Random random) {
        return integerGenerator.generate(random).shortValue();
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Short value) throws SQLException {
        statement.setShort(parameterIndex, value);
    }

    @Override
    public Short get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getShort(columnIndex);
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private int startInclusive = 0;
        private int endExclusive = Short.MAX_VALUE;

        public Builder start(int start) {
            if (start < Short.MIN_VALUE) {
                throw new IllegalArgumentException("invalid start value.  Less than Short.MIN_VALUE.");
            }
            this.startInclusive = start;
            return this;
        }

        public Builder end(int end) {
            if (end > Short.MAX_VALUE) {
                throw new IllegalArgumentException("invalid end value.  Greater than Short.MAX_VALUE.");
            }

            this.endExclusive = end;
            return this;
        }

        @Override
        public ShortGenerator build() {
            return new ShortGenerator(this);
        }
    }

    private ShortGenerator(Builder builder) {
        this.integerGenerator = new IntegerGenerator.Builder().start(builder.startInclusive).end(builder.endExclusive).build();
    }
}
