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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public abstract class AbstractDataGenerator<T> implements DataGenerator<T> {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public final void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex, Random random) throws SQLException {
        T value = generate(random);
        set(connection, statement, parameterIndex, value);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setObject(parameterIndex, value);
    }

    @Override
    public String generateAsString(Random random) {
        T value = generate(random);

        if (value != null) {
            return value.toString();
        }

        return null;
    }

}
