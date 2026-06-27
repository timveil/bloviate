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

import io.bloviate.util.Mixers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Populates a column only for the first {@code prefixSize} rows of each consecutive group of
 * {@code groupSize} rows, emitting {@code null} for the remainder. For the n-th row (0-based) the
 * position within its group is {@code n % groupSize}; positions below {@code prefixSize} delegate
 * to a wrapped generator, the rest yield SQL {@code NULL}.
 *
 * <p>This is the streaming, O(1)-memory building block for a "sparse prefix column" — a value that
 * exists only for a leading subset of each group. For example TPC-C's {@code o_carrier_id}, which is
 * set for the delivered orders (the lower {@code o_id}s of each district) and NULL for the most
 * recent, undelivered ones. It is schema-agnostic.
 *
 * <p>The wrapped generator is consulted only for prefix rows, so a stateless delegate is recommended.
 * The values do not depend on the injected random source beyond whatever the delegate uses.
 *
 * @param <T> the Java type produced by the wrapped generator
 */
public class GroupedPrefixGenerator<T> extends AbstractDataGenerator<T> implements IndexedDataGenerator {

    private final int groupSize;
    private final int prefixSize;
    private final DataGenerator<T> delegate;
    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public T generate() {
        int position = (int) (counter.getAndIncrement() % groupSize);
        return position < prefixSize ? delegate.generate() : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Setting the counter to {@code rowIndex} restores the null/non-null pattern exactly (it is a
     * pure function of the absolute position). The wrapped delegate is consulted only on prefix rows,
     * so it is advanced to the matching delegate-call index: a positional delegate is itself
     * {@link #seek(long) sought} (byte-identical), while a plain random delegate is reseeded
     * deterministically for the partition (its non-key values may differ from a sequential fill).
     */
    @Override
    public void seek(long rowIndex) {
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be non-negative: " + rowIndex);
        }
        counter.set(rowIndex);
        // number of delegate.generate() calls made for rows [0, rowIndex): prefixSize per full group
        // plus the prefix rows of the partial group
        long delegateIndex = (rowIndex / groupSize) * prefixSize + Math.min(rowIndex % groupSize, prefixSize);
        if (delegate instanceof IndexedDataGenerator indexedDelegate) {
            indexedDelegate.seek(delegateIndex);
        } else {
            delegate.reseed(Mixers.splitmix64(delegateIndex));
        }
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, T value) throws SQLException {
        if (value == null) {
            statement.setObject(parameterIndex, null);
        } else {
            delegate.set(connection, statement, parameterIndex, value);
        }
    }

    @Override
    public T get(ResultSet resultSet, int columnIndex) throws SQLException {
        return delegate.get(resultSet, columnIndex);
    }

    public static class Builder<T> extends AbstractBuilder<T> {

        private int groupSize = 1;
        private int prefixSize = 0;
        private DataGenerator<T> delegate;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder<T> groupSize(int groupSize) {
            this.groupSize = groupSize;
            return this;
        }

        /**
         * How many rows at the start of each group receive a (non-null) value from the delegate;
         * the remaining {@code groupSize - prefixSize} rows are NULL.
         */
        public Builder<T> prefixSize(int prefixSize) {
            this.prefixSize = prefixSize;
            return this;
        }

        public Builder<T> delegate(DataGenerator<T> delegate) {
            this.delegate = delegate;
            return this;
        }

        @Override
        public GroupedPrefixGenerator<T> build() {
            return new GroupedPrefixGenerator<>(this);
        }
    }

    private GroupedPrefixGenerator(Builder<T> builder) {
        super(builder.random);
        if (builder.groupSize < 1) {
            throw new IllegalArgumentException("groupSize must be >= 1: " + builder.groupSize);
        }
        if (builder.prefixSize < 0 || builder.prefixSize > builder.groupSize) {
            throw new IllegalArgumentException("prefixSize must be in [0, groupSize]: " + builder.prefixSize);
        }
        if (builder.delegate == null) {
            throw new IllegalStateException("delegate is required");
        }
        this.groupSize = builder.groupSize;
        this.prefixSize = builder.prefixSize;
        this.delegate = builder.delegate;
    }
}
