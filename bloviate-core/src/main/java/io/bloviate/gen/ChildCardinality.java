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

/**
 * Deterministic mapping from a parent row's global (row-major) index to the number
 * of child rows it owns, drawn from the inclusive range {@code [minChildren, maxChildren]}.
 *
 * <p>This models <em>variable parent-child cardinality</em> — a child table whose
 * per-parent row count differs from row to row (e.g. TPC-C orders with a random number
 * of order lines, or TPC-H orders with a random number of line items). It is the shared
 * source of truth that lets a parent table and its child table agree on counts even though
 * they are filled independently: a "number of children" column on the parent (see
 * {@link ChildCountGenerator}) returns {@link #count(long) count(k)} for the k-th parent,
 * and the child's key generators (see {@link ChildKeyComponentGenerator}) emit exactly that
 * many rows for parent k. Because {@link #count(long)} is a pure function of the parent index
 * (a splitmix64 hash, no random state), every consumer computes identical values.
 *
 * @see ChildCountGenerator
 * @see ChildKeyComponentGenerator
 */
public final class ChildCardinality {

    private final int minChildren;
    private final int maxChildren;
    private final long seed;

    /**
     * @param minChildren minimum children per parent (inclusive), {@code >= 0}
     * @param maxChildren maximum children per parent (inclusive), {@code >= minChildren}
     * @param seed        salt mixed into the hash, so different datasets can vary independently
     */
    public ChildCardinality(int minChildren, int maxChildren, long seed) {
        if (minChildren < 0) {
            throw new IllegalArgumentException("minChildren must be non-negative: " + minChildren);
        }
        if (maxChildren < minChildren) {
            throw new IllegalArgumentException("maxChildren (" + maxChildren + ") must be >= minChildren (" + minChildren + ")");
        }
        this.minChildren = minChildren;
        this.maxChildren = maxChildren;
        this.seed = seed;
    }

    /**
     * Returns the deterministic child count for the parent at the given global index.
     *
     * @param parentIndex the 0-based, row-major parent index
     * @return a value in {@code [minChildren, maxChildren]}
     */
    public int count(long parentIndex) {
        int range = maxChildren - minChildren + 1;
        if (range == 1) {
            return minChildren;
        }
        return minChildren + (int) Math.floorMod(mix(parentIndex + seed), range);
    }

    /**
     * Returns the total number of child rows across the first {@code parentCount} parents,
     * i.e. {@code sum(count(k))} for {@code k} in {@code [0, parentCount)}.
     *
     * <p>When the count is fixed ({@code minChildren == maxChildren}) this is closed-form
     * and O(1). Otherwise it is a single O(parentCount) pass with no intermediate storage,
     * so memory stays constant even for very large datasets.
     *
     * @param parentCount the number of parents
     * @return the total child-row count
     */
    public long total(long parentCount) {
        if (minChildren == maxChildren) {
            return parentCount * minChildren;
        }
        long sum = 0;
        for (long k = 0; k < parentCount; k++) {
            sum += count(k);
        }
        return sum;
    }

    public int minChildren() {
        return minChildren;
    }

    public int maxChildren() {
        return maxChildren;
    }

    // splitmix64 finalizer — a well-distributed, deterministic mix of a 64-bit input
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
