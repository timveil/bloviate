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

/**
 * Generator for Boolean values.
 * Produces random true/false values for database columns that store boolean data.
 *
 * @since 1.0.0
 */
public class BooleanGenerator extends AbstractDataGenerator<Boolean> {

    @Override
    public Boolean generate() {
        return random.nextBoolean();
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setBoolean(parameterIndex, (Boolean) value);
    }

    @Override
    public Boolean get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getBoolean(columnIndex);
    }

    /**
     * Builder for creating BooleanGenerator instances.
     */
    public static class Builder extends AbstractBuilder {

        /**
         * Constructs a new Builder for BooleanGenerator.
         *
         * @param random the random number generator to use
         */
        public Builder(Random random) {
            super(random);
        }

        @Override
        public BooleanGenerator build() {
            return new BooleanGenerator(this);
        }
    }

    private BooleanGenerator(Builder builder) {
        super(builder.random);

    }
}
