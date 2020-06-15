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

import java.sql.*;

public class SqlClobGenerator extends AbstractDataGenerator<Clob> {
    //todo

    @Override
    public Clob generate() {
        return null;
    }

    @Override
    public String generateAsString() {
        return null;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setClob(parameterIndex, (Clob) value);
    }

    @Override
    public Clob get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getClob(columnIndex);
    }

    public static class Builder {
        public SqlClobGenerator build() {
            return new SqlClobGenerator(this);
        }
    }

    private SqlClobGenerator(Builder builder) {

    }
}
