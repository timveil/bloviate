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

import io.bloviate.gen.DataGenerator;

import java.util.random.RandomGenerator;

/**
 * Builds the {@link DataGenerator} used to override generation for a single column.
 *
 * <p>The fill engine supplies a {@link RandomGenerator} that is already seeded for the
 * target column, so generators created here participate in the same
 * reproducible, per-column seeding (and foreign-key reseeding) as
 * auto-detected generators. Implementations should build their generator from
 * the supplied {@code random} rather than creating their own.
 *
 * @see ColumnConfiguration
 * @since 1.0.0
 */
@FunctionalInterface
public interface ColumnGeneratorFactory {

    /**
     * Creates the generator for the configured column.
     *
     * @param random the column-seeded random source provided by the fill engine
     * @return the generator to use for the column
     */
    DataGenerator<?> create(RandomGenerator random);
}
