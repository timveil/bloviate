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
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class IntegerArrayGenerator extends AbstractDataGenerator<Integer[]> {

    private final int length;
    private final DataGenerator<? extends Integer> elementGenerator;

    @Override
    public Integer[] generate() {
        Integer[] random = new Integer[length];

        for (int i = 0; i < length; i++) {
            if (elementGenerator != null) {
                random[i] = elementGenerator.generate();
            } else {
                random[i] = RandomUtils.nextInt();
            }
        }

        return random;

    }

    @Override
    public String generateAsString() {
        return null;
    }

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        statement.setArray(parameterIndex, connection.createArrayOf(JDBCType.INTEGER.getName(), generate()));
    }

    public static class Builder {

        private int length = 3;
        private DataGenerator<? extends Integer> elementGenerator;

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder elementGenerator(DataGenerator<? extends Integer> generator) {
            this.elementGenerator = generator;
            return this;
        }

        public IntegerArrayGenerator build() {
            return new IntegerArrayGenerator(this);
        }
    }

    private IntegerArrayGenerator(Builder builder) {
        this.length = builder.length;
        this.elementGenerator = builder.elementGenerator;
    }
}
