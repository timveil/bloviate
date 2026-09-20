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

package io.bloviate.db;

import io.bloviate.gen.RelativeWindow;

/**
 * Overrides the data generator used for a single column, replacing the
 * generator that would otherwise be auto-detected from the column's JDBC type.
 *
 * <p>The column name is matched case-insensitively. The generator is built lazily
 * by the fill engine via {@link ColumnGeneratorFactory#create(java.util.random.RandomGenerator)},
 * using a column-seeded {@link java.util.random.RandomGenerator} so overridden columns remain
 * reproducible.
 *
 * @param columnName the name of the column to override (matched case-insensitively)
 * @param generatorFactory builds the generator for the column
 * @see TableConfiguration
 * @since 1.0.0
 */
public record ColumnConfiguration(String columnName, ColumnGeneratorFactory generatorFactory) {

    /**
     * Overrides a column with a generator that draws from a {@link RelativeWindow}, measured from the
     * fill's {@code asOf} anchor (see {@code DatabaseFiller.Builder#asOf}):
     *
     * <pre>{@code
     * ColumnConfiguration.relative("placed_at", RelativeWindow.withinLast("90d"),
     *         (random, window) -> new SqlTimestampGenerator.Builder(random).window(window).build());
     * }</pre>
     *
     * <p>Shorthand for {@code new ColumnConfiguration(column, ColumnGeneratorFactory.relative(window, factory))}.
     *
     * @param columnName the name of the column to override (matched case-insensitively)
     * @param window     the window, relative to the anchor
     * @param factory    builds the generator from the seeded random source and the resolved window
     * @return the column configuration
     * @since 3.7.0
     */
    public static ColumnConfiguration relative(String columnName, RelativeWindow window,
                                               ColumnGeneratorFactory.Windowed factory) {
        return new ColumnConfiguration(columnName, ColumnGeneratorFactory.relative(window, factory));
    }
}
