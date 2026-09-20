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
import io.bloviate.db.GenerationContext;
import io.bloviate.gen.DataGenerator;

import java.util.Objects;
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

    /**
     * Creates a generator for the given column, given what the fill knows besides the seed. The
     * engine calls this overload; by default it ignores the context and calls
     * {@link #create(Column, RandomGenerator)}, so an existing factory needs no change.
     *
     * @param column  the column metadata, including type, size, and database-specific type name
     * @param random  the seeded random source supplied by the fill engine
     * @param context the fill's context: the {@code asOf} anchor shared by every table and worker
     * @return the generator to use for the column
     * @since 3.7.0
     */
    default DataGenerator<?> create(Column column, RandomGenerator random, GenerationContext context) {
        return create(column, random);
    }

    /**
     * A factory that is built from the fill's {@link GenerationContext}, for example to resolve a
     * {@link io.bloviate.gen.RelativeWindow} against {@link GenerationContext#asOf()} in a registry rule:
     *
     * <pre>{@code
     * new GeneratorRegistry.Builder().registerColumnNamePattern("(?i).*_at", GeneratorFactory.contextual(
     *         (column, random, context) -> new SqlTimestampGenerator.Builder(random)
     *                 .window(RelativeWindow.withinLast("90d").resolve(context.asOf())).build()));
     * }</pre>
     *
     * <p>Called without a context, through {@link #create(Column, RandomGenerator)}, the factory reads an
     * {@linkplain GenerationContext#unpinned() unpinned} one, that is, the current UTC day.
     *
     * @param factory builds the generator from the column, the seeded random source and the context
     * @return a factory the engine will call with the fill's context
     * @since 3.7.0
     */
    static GeneratorFactory contextual(Contextual factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new GeneratorFactory() {
            @Override
            public DataGenerator<?> create(Column column, RandomGenerator random) {
                return factory.create(column, random, GenerationContext.unpinned());
            }

            @Override
            public DataGenerator<?> create(Column column, RandomGenerator random, GenerationContext context) {
                return factory.create(column, random, context);
            }
        };
    }

    /**
     * A factory that also receives the fill's {@link GenerationContext}; see {@link #contextual}.
     *
     * @since 3.7.0
     */
    @FunctionalInterface
    interface Contextual {

        /**
         * Creates a generator for the given column.
         *
         * @param column  the column metadata, including type, size, and database-specific type name
         * @param random  the seeded random source supplied by the fill engine
         * @param context the fill's context
         * @return the generator to use for the column
         */
        DataGenerator<?> create(Column column, RandomGenerator random, GenerationContext context);
    }
}
