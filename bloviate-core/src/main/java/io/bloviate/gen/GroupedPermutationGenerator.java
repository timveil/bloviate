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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits a deterministic random <em>permutation</em> of {@code [start, start + groupSize)} within
 * each consecutive run of {@code groupSize} rows, advancing to a fresh permutation for the next
 * group. Over the n-th row (0-based) it returns {@code start + permute(n % groupSize)} using a
 * permutation keyed by {@code n / groupSize}, so each group is an independent shuffle.
 *
 * <p>This is the streaming, O(1)-memory building block for "shuffled 1:1 assignment within a
 * group" — for example TPC-C's {@code o_c_id}, where each district's orders reference its customer
 * ids {@code 1..C} in a random order (each customer gets exactly one order). It is schema-agnostic:
 * any column that should be a per-group permutation of a contiguous id range can use it.
 *
 * <p>The permutation is produced by a small keyed Feistel network with cycle-walking, which is a
 * bijection on {@code [0, groupSize)} without materialising a shuffle array — so memory is constant
 * regardless of group size or row count, and the result is fully reproducible. The values do not
 * depend on the injected random source; the per-group key is derived from a fixed {@code seed} and
 * the group index, and counter state advances on every {@link #generate()}.
 *
 * <p>Supports group sizes up to {@code 2^30}.
 */
public class GroupedPermutationGenerator extends AbstractDataGenerator<Integer> implements IndexedDataGenerator {

    private static final int ROUNDS = 4;

    private final int groupSize;
    private final int start;
    private final long seed;
    private final int halfBits;
    private final long halfMask;
    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public Integer generate() {
        long n = counter.getAndIncrement();
        long group = n / groupSize;
        int position = (int) (n % groupSize);
        long key = mix(seed + group);
        return start + permute(position, key);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The value is a pure function of the row index (the permutation is keyed by
     * {@code rowIndex / groupSize} and applied to {@code rowIndex % groupSize}), so seeking is O(1):
     * the counter is set to {@code rowIndex}.
     */
    @Override
    public void seek(long rowIndex) {
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be non-negative: " + rowIndex);
        }
        counter.set(rowIndex);
    }

    // cycle-walking: a Feistel permutation on [0, 2^(2*halfBits)) restricted to [0, groupSize)
    private int permute(int position, long key) {
        long value = position;
        do {
            value = feistel(value, key);
        } while (value >= groupSize);
        return (int) value;
    }

    private long feistel(long value, long key) {
        long left = (value >>> halfBits) & halfMask;
        long right = value & halfMask;
        for (int round = 0; round < ROUNDS; round++) {
            long f = roundFunction(right, round, key) & halfMask;
            long nextLeft = right;
            long nextRight = left ^ f;
            left = nextLeft;
            right = nextRight;
        }
        return (left << halfBits) | right;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        statement.setInt(parameterIndex, value);
    }

    @Override
    public Integer get(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    public static class Builder extends AbstractBuilder<Integer> {

        private int groupSize = 1;
        private int start = 0;
        private long seed = 0;

        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * The size of each permutation group: every consecutive run of this many rows is an
         * independent shuffle (a within-group permutation) of {@code [start, start + groupSize)}, after
         * which a fresh permutation begins. It is the {@code groupSize} divisor in
         * {@code start + permute(rowIndex % groupSize)} keyed by {@code rowIndex / groupSize}. Must be
         * in {@code [1, 2^30]}, otherwise {@link #build()} throws. Defaults to {@code 1}.
         *
         * @param groupSize the number of rows per permutation block; must be in {@code [1, 2^30]}
         * @return this builder, for chaining
         */
        public Builder groupSize(int groupSize) {
            this.groupSize = groupSize;
            return this;
        }

        /**
         * The lowest value of the permuted range, inclusive: each group is a permutation of
         * {@code [start, start + groupSize)} (the {@code start} offset added to each permuted index).
         * Defaults to {@code 0}.
         *
         * @param start the lowest value of the permuted range, inclusive
         * @return this builder, for chaining
         */
        public Builder start(int start) {
            this.start = start;
            return this;
        }

        /**
         * Salt for the per-group permutation key. The key for each group is derived deterministically
         * from this seed and the group index, so a fixed seed yields a fully reproducible shuffle for
         * every group (independent of the injected random source). Change it to obtain a different but
         * equally reproducible set of per-group orderings. Defaults to {@code 0}.
         *
         * @param seed the salt mixed with the group index to key each within-group permutation
         * @return this builder, for chaining
         */
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Builds the generator.
         *
         * @return a new {@link GroupedPermutationGenerator}
         * @throws IllegalArgumentException if {@code groupSize} is not in {@code [1, 2^30]}
         */
        @Override
        public GroupedPermutationGenerator build() {
            return new GroupedPermutationGenerator(this);
        }
    }

    private GroupedPermutationGenerator(Builder builder) {
        super(builder.random);
        if (builder.groupSize < 1 || builder.groupSize > (1 << 30)) {
            throw new IllegalArgumentException("groupSize must be in [1, 2^30]: " + builder.groupSize);
        }
        this.groupSize = builder.groupSize;
        this.start = builder.start;
        this.seed = builder.seed;

        // split point for a balanced Feistel whose domain (2^(2*halfBits)) covers groupSize
        int bitsNeeded = groupSize <= 1 ? 0 : (32 - Integer.numberOfLeadingZeros(groupSize - 1));
        this.halfBits = (bitsNeeded + 1) / 2;
        this.halfMask = halfBits == 0 ? 0L : ((1L << halfBits) - 1);
    }

    private static long roundFunction(long right, int round, long key) {
        long z = right * 0x9E3779B97F4A7C15L + key + (round + 1L) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // splitmix64 finalizer — a well-distributed, deterministic mix of a 64-bit input
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
