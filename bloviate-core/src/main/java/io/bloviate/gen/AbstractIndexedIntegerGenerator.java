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
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

/**
 * Base for {@code Integer} generators whose value is a pure function of the row index.
 *
 * <p>Subclasses implement only {@link #valueAt(long)}. This class owns the row counter and
 * derives both behaviours from it: {@link #generate()} evaluates the function at the next
 * index, and {@link #seek(long)} jumps the counter, which is what makes seeking O(1) rather
 * than a replay of every preceding row.
 *
 * <p>Keeping the counter here means the O(1)-seek invariant is stated once instead of being
 * re-implemented per generator. A subclass cannot accidentally weaken it: {@link #generate()}
 * and {@link #seek(long)} are {@code final}, so the only thing left to vary is the function
 * itself.
 *
 * <p>This is deliberately package-private. It is an internal way of sharing plumbing between
 * the generators in this package, not a published extension point &mdash; third-party
 * generators extend {@link AbstractDataGenerator} and implement {@link IndexedDataGenerator}
 * directly. Promoting this to public API later is easy; un-publishing it would not be.
 *
 * @see IndexedDataGenerator
 */
abstract class AbstractIndexedIntegerGenerator extends AbstractDataGenerator<Integer> implements IndexedDataGenerator {

    private final AtomicLong counter = new AtomicLong(0);

    protected AbstractIndexedIntegerGenerator(RandomGenerator random) {
        super(random);
    }

    /**
     * Returns the value for a given 0-based row index.
     *
     * <p>Must be a pure function of {@code rowIndex}: calling it twice with the same index must
     * return the same value, and it must not consult the counter or the random source. That is
     * the property {@link #seek(long)} relies on.
     *
     * @param rowIndex the 0-based row index
     * @return the value for that row
     */
    protected abstract Integer valueAt(long rowIndex);

    @Override
    public final Integer generate() {
        return valueAt(counter.getAndIncrement());
    }

    /**
     * {@inheritDoc}
     *
     * <p>O(1): because {@link #valueAt(long)} is a closed form of the row index, positioning is
     * just a counter assignment &mdash; no rows are replayed.
     */
    @Override
    public final void seek(long rowIndex) {
        if (rowIndex < 0) {
            throw new IllegalArgumentException("rowIndex must be non-negative: " + rowIndex);
        }
        counter.set(rowIndex);
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
}
