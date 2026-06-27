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

package io.bloviate.util;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Central factory for the {@link RandomGenerator} used throughout the fill engine.
 *
 * <p>Bloviate seeds a fresh, isolated generator per column (see
 * {@link DatabaseUtils#columnSeed(io.bloviate.db.Column, long)}), so reproducible parallel
 * generation never depends on a shared, mutable RNG. The only thing this class fixes is the
 * <em>algorithm</em>: {@value #ALGORITHM}, the JDK general-purpose default
 * ({@link RandomGeneratorFactory#getDefault()}). Compared to the legacy {@link java.util.Random}
 * (a 48-bit LCG with a documented statistical defect and {@code synchronized} methods), it offers a
 * far longer period (2<sup>128</sup>&nbsp;&times;&nbsp;2<sup>64</sup>), better statistical quality,
 * and no lock overhead &mdash; and it is explicitly recommended for multi-threaded use with a
 * per-unit instance, exactly Bloviate's per-column model.
 *
 * <p>Because {@link java.util.Random} also implements {@link RandomGenerator}, callers that still
 * hold a {@code java.util.Random} can pass it directly anywhere a {@link RandomGenerator} is
 * expected; new code should obtain generators here so the whole engine shares one algorithm.
 *
 * @since 2.10.0
 */
public final class RandomGenerators {

    /**
     * The {@link RandomGenerator} algorithm used by the fill engine.
     */
    public static final String ALGORITHM = "L64X128MixRandom";

    /**
     * The factory is immutable and thread-safe, so a single shared instance backs every
     * {@link #create(long)} call.
     */
    private static final RandomGeneratorFactory<RandomGenerator> FACTORY =
            RandomGeneratorFactory.of(ALGORITHM);

    private RandomGenerators() {
    }

    /**
     * Creates a new, independent {@link RandomGenerator} seeded with the given value. The same seed
     * always yields the same sequence, which is what makes Bloviate's per-column generation
     * reproducible.
     *
     * @param seed the seed value
     * @return a freshly seeded generator
     */
    public static RandomGenerator create(long seed) {
        return FACTORY.create(seed);
    }
}
