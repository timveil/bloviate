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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.Random;

/**
 * Generator for SQL {@code STRUCT} (structured / user-defined type) columns.
 *
 * <p><strong>{@link #generate()} intentionally returns {@code null}.</strong> A {@link Struct}
 * cannot be synthesized in memory: the only portable way to create one is
 * {@code Connection.createStruct(typeName, attributes)}, which requires a live {@link
 * java.sql.Connection}, the SQL type name, and one correctly typed value per attribute. The
 * {@link #generate()} contract exposes none of these, and the JDBC drivers this library targets
 * (PostgreSQL, MySQL, CockroachDB) do not implement {@code createStruct} — it throws
 * {@link java.sql.SQLFeatureNotSupportedException}. Returning {@code null} lets nullable
 * {@code STRUCT} columns fill; a non-nullable one will fail at insert.
 *
 * <p>{@link #get(ResultSet, int)} reads an existing {@code Struct} normally, so round-tripping a
 * value already present in a {@link ResultSet} works.
 */
public class SqlStructGenerator extends AbstractDataGenerator<Struct> {

    /**
     * {@inheritDoc}
     *
     * <p>Always returns {@code null}; see the class documentation for why a {@code Struct} cannot
     * be generated.
     */
    @Override
    public Struct generate() {
        return null;
    }

    @Override
    public String generateAsString() {
        return null;
    }

    @Override
    public Struct get(ResultSet resultSet, int columnIndex) throws SQLException {
        return (Struct) resultSet.getObject(columnIndex);
    }

    public static class Builder extends AbstractBuilder<Struct> {

        public Builder(Random random) {
            super(random);
        }

        @Override
        public SqlStructGenerator build() {
            return new SqlStructGenerator(this);
        }
    }

    private SqlStructGenerator(Builder builder) {
        super(builder.random);

    }
}
