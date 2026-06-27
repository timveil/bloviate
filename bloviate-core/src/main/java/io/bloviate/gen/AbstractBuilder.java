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

import java.time.Instant;
import java.util.Random;

/**
 * Abstract base class for data generator builders.
 * Provides common functionality for all builder implementations,
 * including random number generation support.
 *
 * @param <T> the Java type of values produced by the built generator
 * @since 1.0.0
 */
public abstract class AbstractBuilder<T> implements Builder<T> {

    /**
     * Fixed reference instant used as the deterministic stand-in for "now" when a temporal
     * generator's bounds are left at their defaults. Anchoring the default range to a constant
     * rather than {@link Instant#now()} keeps generated date/time/timestamp data
     * <strong>reproducible</strong>: the same schema filled with the same seed yields identical
     * temporal values on every run. Callers that need a different window still set explicit bounds.
     */
    protected static final Instant DEFAULT_REFERENCE = Instant.parse("2020-01-01T00:00:00Z");

    /**
     * The random number generator used for creating data.
     */
    protected final Random random;

    /**
     * Constructs a new AbstractBuilder with the specified random number generator.
     *
     * @param random the random number generator to use for data generation
     */
    public AbstractBuilder(Random random) {
        this.random = random;
    }
}
