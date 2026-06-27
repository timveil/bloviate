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

package io.bloviate.gen.tpcc;

import io.bloviate.util.SeededRandomUtils;

/**
 * Utilities implementing pieces of the TPC-C specification.
 */
public final class TPCCUtils {

    private TPCCUtils() {
    }

    /**
     * The TPC-C non-uniform random function {@code NURand} (clause 2.1.6):
     * {@code NURand(A, x, y) = (((random(0, A) | random(x, y)) + C) % (y - x + 1)) + x}.
     *
     * @param a    the {@code A} constant (a bit-mask bound; one of 255, 1023 or 8191)
     * @param c    the run-time constant {@code C}, chosen within {@code [0, A]}
     * @param min  the inclusive lower bound {@code x}
     * @param max  the inclusive upper bound {@code y}
     * @param randomUtils the seeded random source
     * @return a non-uniformly distributed value in {@code [min, max]}
     */
    public static int nonUniformRandom(int a, int c, int min, int max, SeededRandomUtils randomUtils) {
        return (((randomUtils.nextInt(0, a + 1) | randomUtils.nextInt(min, max + 1)) + c) % (max - min + 1)) + min;
    }
}
