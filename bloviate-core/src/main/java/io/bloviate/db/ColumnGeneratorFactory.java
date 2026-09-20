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
import io.bloviate.gen.RelativeWindow;

import java.util.Objects;
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
 * <p>A factory that needs to know something about the fill besides the seed, such as the {@code asOf}
 * anchor of a {@link RelativeWindow}, is made with {@link #contextual} or {@link #relative}; the engine
 * then calls {@link #create(RandomGenerator, GenerationContext)}. A plain lambda is still a valid factory
 * and behaves exactly as it always has.
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

    /**
     * Creates the generator for the configured column, given what the fill knows besides the seed. The
     * engine calls this overload; by default it ignores the context and calls
     * {@link #create(RandomGenerator)}, so an existing factory needs no change.
     *
     * @param random  the column-seeded random source provided by the fill engine
     * @param context the fill's context: the {@code asOf} anchor shared by every table and worker
     * @return the generator to use for the column
     * @since 3.7.0
     */
    default DataGenerator<?> create(RandomGenerator random, GenerationContext context) {
        return create(random);
    }

    /**
     * A factory that is built from the fill's {@link GenerationContext}, for example to resolve a
     * {@link RelativeWindow} against {@link GenerationContext#asOf()}:
     *
     * <pre>{@code
     * ColumnGeneratorFactory.contextual((random, context) -> new InstantGenerator.Builder(random)
     *         .window(RelativeWindow.withinLast("30d").resolve(context.asOf())).build());
     * }</pre>
     *
     * <p>Called without a context, through {@link #create(RandomGenerator)}, the factory reads an
     * {@linkplain GenerationContext#unpinned() unpinned} one, that is, the current UTC day.
     *
     * @param factory builds the generator from the seeded random source and the context
     * @return a factory the engine will call with the fill's context
     * @since 3.7.0
     */
    static ColumnGeneratorFactory contextual(Contextual factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new ColumnGeneratorFactory() {
            @Override
            public DataGenerator<?> create(RandomGenerator random) {
                return factory.create(random, GenerationContext.unpinned());
            }

            @Override
            public DataGenerator<?> create(RandomGenerator random, GenerationContext context) {
                return factory.create(random, context);
            }
        };
    }

    /**
     * A factory whose generator draws from a {@link RelativeWindow}: the window is resolved once, against
     * the fill's {@code asOf} anchor, when the generator is created.
     *
     * <pre>{@code
     * ColumnGeneratorFactory.relative(RelativeWindow.withinLast("90d"),
     *         (random, window) -> new SqlTimestampGenerator.Builder(random).window(window).build());
     * }</pre>
     *
     * @param window  the window, relative to the anchor
     * @param factory builds the generator from the seeded random source and the resolved window
     * @return a factory the engine will call with the fill's context
     * @throws NullPointerException if either argument is null
     * @since 3.7.0
     */
    static ColumnGeneratorFactory relative(RelativeWindow window, Windowed factory) {
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        return contextual((random, context) -> factory.create(random, window.resolve(context.asOf())));
    }

    /**
     * A factory that also receives the fill's {@link GenerationContext}; see {@link #contextual}.
     *
     * @since 3.7.0
     */
    @FunctionalInterface
    interface Contextual {

        /**
         * Creates the generator for the configured column.
         *
         * @param random  the column-seeded random source provided by the fill engine
         * @param context the fill's context
         * @return the generator to use for the column
         */
        DataGenerator<?> create(RandomGenerator random, GenerationContext context);
    }

    /**
     * A factory that receives a {@link RelativeWindow} already resolved against the fill's anchor; see
     * {@link #relative}.
     *
     * @since 3.7.0
     */
    @FunctionalInterface
    interface Windowed {

        /**
         * Creates the generator for the configured column.
         *
         * @param random the column-seeded random source provided by the fill engine
         * @param window the window resolved against the fill's {@code asOf}
         * @return the generator to use for the column
         */
        DataGenerator<?> create(RandomGenerator random, RelativeWindow.Resolved window);
    }
}
