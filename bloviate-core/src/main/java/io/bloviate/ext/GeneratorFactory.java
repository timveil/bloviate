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

package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.gen.DataGenerator;

import java.util.random.RandomGenerator;

/**
 * Builds the {@link DataGenerator} used for a column of a particular JDBC type.
 *
 * <p>{@link AbstractDatabaseSupport} keeps a registry of these keyed by
 * {@link java.sql.JDBCType}; database-specific subclasses add or replace entries to
 * customize generation (for example, to handle driver-specific type names exposed via
 * {@link Column#typeName()}).
 *
 * @see AbstractDatabaseSupport
 * @since 1.0.0
 */
@FunctionalInterface
public interface GeneratorFactory {

    /**
     * Creates a generator for the given column.
     *
     * @param column the column metadata, including type, size, and database-specific type name
     * @param random the seeded random source supplied by the fill engine
     * @return the generator to use for the column
     */
    DataGenerator<?> create(Column column, RandomGenerator random);
}
