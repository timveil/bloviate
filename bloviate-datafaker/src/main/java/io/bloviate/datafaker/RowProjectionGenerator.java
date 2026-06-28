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

package io.bloviate.datafaker;

import io.bloviate.gen.AbstractDataGenerator;
import io.bloviate.gen.IndexedDataGenerator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits one field of a {@link RowContext}'s per-row entity. Several projections of the same context
 * share that context, so they produce a consistent tuple per row (issue #473).
 *
 * <p>Each projection keeps its own row counter and is {@link IndexedDataGenerator seekable}, so the
 * projections of a row advance in lockstep, stay order-independent, and reproduce the sequential
 * output under intra-table partitioning (each partition seeks to its first row, then walks). The
 * value is a pure function of the row index via the shared context, so partitioned output is
 * byte-identical to a sequential fill.
 *
 * @param <E> the entity type
 * @since 2.12.0
 */
public final class RowProjectionGenerator<E> extends AbstractDataGenerator<String> implements IndexedDataGenerator {

    private final RowContext<E> context;
    private final Function<E, String> selector;
    private final AtomicLong counter = new AtomicLong(0);

    RowProjectionGenerator(RandomGenerator random, RowContext<E> context, Function<E, String> selector) {
        super(random);
        this.context = context;
        this.selector = selector;
    }

    /**
     * Projects the selected field of the entity for the current row, then advances this
     * projection's row counter by one. The entity is resolved from the shared {@link RowContext}
     * for the current row index, so sibling projections of the same context yield a consistent
     * tuple.
     *
     * @return the selected field of the current row's entity, as a string
     */
    @Override
    public String generate() {
        return selector.apply(context.at(counter.getAndIncrement()));
    }

    /**
     * Positions this projection's row counter at the given row index, so the next
     * {@link #generate()} projects that row's entity. Used for positional or partitioned access,
     * where each partition seeks to its first row and then walks forward.
     *
     * @param rowIndex the zero-based row index to position at
     * @throws IllegalArgumentException if {@code rowIndex} is negative
     */
    @Override
    public void seek(long rowIndex) {
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be non-negative: " + rowIndex);
        }
        counter.set(rowIndex);
    }

    /**
     * Reads this projection's value back from a {@link ResultSet} as a string.
     *
     * @param resultSet   the result set positioned on the row to read
     * @param columnIndex the one-based column index to read
     * @return the column value as a string
     * @throws SQLException if the value cannot be read from the result set
     */
    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }
}
