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
 * A {@link DataGenerator} whose value for a row is a deterministic function of that row's
 * <em>absolute</em> (0-based) index, so it can be positioned to any row without replaying every
 * preceding {@code generate()} call.
 *
 * <p>This is what lets the fill engine partition a single table's rows across workers (intra-table
 * parallelism): a worker filling the range {@code [start, end)} calls {@link #seek(long) seek(start)}
 * once and then iterates {@code generate()} as usual, producing exactly the values the sequential
 * fill produces at those rows. Implemented by the counter/cursor-based generators whose output is
 * positional &mdash; the key, sequence, child-count, permutation, and prefix generators &mdash; so
 * that key columns and the columns correlated with them stay byte-for-byte identical regardless of
 * how the rows are partitioned (preserving foreign-key validity).
 *
 * <p>Plain random generators are intentionally <em>not</em> indexed: their values carry no cross-row
 * contract, so the engine reseeds them per partition instead.
 *
 * @since 2.10.0
 * @see DataGenerator
 */
public interface IndexedDataGenerator {

    /**
     * Positions this generator so the next {@link DataGenerator#generate()} returns the value for
     * the given absolute (0-based) row index.
     *
     * @param rowIndex the absolute, 0-based row index to seek to; must be {@code >= 0}
     */
    void seek(long rowIndex);
}
