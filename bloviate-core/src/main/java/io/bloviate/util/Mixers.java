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

/**
 * Deterministic integer mixing functions shared across the engine.
 *
 * <p>{@link #splitmix64(long)} is the splitmix64 finalizer: a fast, well-distributed bijection on
 * 64-bit values with no state. The fill engine uses it to derive one reproducible value from another
 * &mdash; for example a per-partition seed from a column seed and a starting row offset, or a child
 * count from a parent index &mdash; without pulling from a stateful random source.
 *
 * @since 2.10.0
 */
public final class Mixers {

    private Mixers() {
    }

    /**
     * The splitmix64 finalizer applied to {@code value}.
     *
     * @param value the input
     * @return a well-distributed, deterministic mix of {@code value}
     */
    public static long splitmix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
