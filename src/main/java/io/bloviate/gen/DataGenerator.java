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

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
public interface DataGenerator<T> {
    T generate();

    String generateAsString();

    void setSeed(long seed);

    void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException;

    void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException;

    T get(ResultSet resultSet, int columnIndex) throws SQLException;
}
