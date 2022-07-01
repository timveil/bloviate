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
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public class IntegerArrayGenerator extends AbstractDataGenerator<Integer[]> {

    private final int length;

    @Override
    public Integer[] generate(Random random) {
        Integer[] randomArray = new Integer[length];

        for (int i = 0; i < length; i++) {
            randomArray[i] = random.nextInt();
        }

        return randomArray;

    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer[] value) throws SQLException {
        statement.setArray(parameterIndex, connection.createArrayOf(JDBCType.INTEGER.getName(), value));
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private int length = 3;

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        @Override
        public IntegerArrayGenerator build() {
            return new IntegerArrayGenerator(this);
        }
    }

    private IntegerArrayGenerator(Builder builder) {
        this.length = builder.length;
    }
}
