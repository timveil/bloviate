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

/**
 * A {@link RandomGenerator} whose stream can be repositioned to any row index in O(1), making every
 * value drawn from it a pure function of {@code (seed, index, drawOrdinal)}.
 *
 * <p>The fill engine creates one instance per column and calls {@link #position(long)} with the
 * absolute row index before generating each cell (see {@code TableFiller}). Because the value for row
 * {@code k} no longer depends on how many draws earlier rows consumed, a worker can start generating
 * at any row without replaying the rows before it &mdash; the property that makes intra-table
 * partition seeks O(1) and partitioned fills byte-identical to sequential ones. Repositioning mutates
 * this instance <em>in place</em>, so delegate generators and {@code SeededRandomUtils} views that
 * captured the reference at construction follow automatically.
 *
 * <p>The stream is the SplitMix64 sequence: draw {@code j} after {@code position(k)} is
 * {@code splitmix64(seed + splitmix64(k) + j * GOLDEN_GAMMA)}. The index passes through the
 * {@code splitmix64} bijection before entering the state so that nearby seeds can never alias
 * shifted row ranges of one another &mdash; column seeds are derived arithmetically from the user's
 * base seed, so without the mix, base seed {@code s + 1} would replay most of base seed {@code s}'s
 * rows at an offset. Draws within one row advance by the 64-bit golden gamma. Statistical quality
 * matches {@link java.util.SplittableRandom}, which uses the same construction. {@code nextGaussian}
 * and the bounded {@code next*} methods are the stateless {@link RandomGenerator} defaults, so no
 * hidden cross-draw state survives a reposition.
 *
 * <p>Not thread-safe: like every engine random source, an instance belongs to exactly one column of
 * one fill worker.
 *
 * @since 3.0.0
 * @see RandomGenerators
 * @see Mixers#splitmix64(long)
 */
public final class IndexedRandom implements RandomGenerator {

    /** The 64-bit golden ratio increment used by SplitMix64 to space successive draws. */
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final long seed;
    private long state;

    /**
     * Creates a generator positioned at index {@code 0}.
     *
     * @param seed the column seed; the same seed and positions always replay the same values
     */
    public IndexedRandom(long seed) {
        this.seed = seed;
        this.state = seed + Mixers.splitmix64(0);
    }

    /**
     * Repositions the stream so subsequent draws produce the values for the given index,
     * regardless of how many draws were consumed before this call.
     *
     * @param index the absolute, 0-based index to position at; must be {@code >= 0}
     */
    public void position(long index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0: " + index);
        }
        // the mix keeps arithmetically-related seeds (and columns whose seeds sit close together)
        // from aliasing each other's rows at a fixed offset; see the class javadoc
        this.state = seed + Mixers.splitmix64(index);
    }

    @Override
    public long nextLong() {
        state += GOLDEN_GAMMA;
        return Mixers.splitmix64(state);
    }
}
