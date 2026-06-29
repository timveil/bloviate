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

package io.bloviate.db;

import io.bloviate.ext.DatabaseSupport;

/**
 * How {@link DatabaseFiller} orders table fills relative to foreign-key dependencies.
 *
 * <p>By default ({@link #ordered()}) the fill engine respects the foreign-key dependency graph:
 * a child table is never filled before its parent. On the parallel path this means filling one
 * topological <em>level</em> at a time with a barrier between levels, which serializes deep, narrow
 * dependency chains (e.g. TPC-C's {@code warehouse → district → customer → open_order → order_line}).
 *
 * <p>{@link #unorderedBulk()} trades that ordering for speed on chained schemas. Bloviate's generated
 * data is referentially consistent <em>by construction</em> — a foreign-key column's generator is
 * seeded from the referenced primary-key column's seed, so child and parent produce identical key
 * values regardless of insert order (see
 * {@link io.bloviate.util.DatabaseUtils#columnSeed(Column, long)}). That makes it safe to disable
 * foreign-key enforcement for the duration of the fill, insert <em>all</em> tables concurrently with
 * no topological barrier, then re-enable enforcement. For the same seed, the resulting row content is
 * identical to an ordered fill across every deterministic column — physical (on-disk) row order may
 * differ, and columns whose generator is non-deterministic (e.g. wall-clock temporal values, or a
 * custom plugin that reads external state) are reproducible only to the extent that generator is.
 *
 * <p>Bulk mode only takes effect on the parallel path (a {@link javax.sql.DataSource} with
 * {@link DatabaseFiller.Builder#threads(int)} {@code > 1}) and only when the active
 * {@link DatabaseSupport} reports {@link DatabaseSupport#supportsBulkLoad()}. Otherwise the engine
 * logs a warning and uses the ordered path. PostgreSQL and MySQL support it; CockroachDB does not and
 * falls back.
 *
 * @param mode       the ordering mode
 * @param revalidate whether {@link DatabaseSupport#enableConstraints} should re-validate existing rows
 *                   when re-enabling enforcement (only meaningful for vendor mechanisms that can do so;
 *                   the session-based PostgreSQL/MySQL mechanisms cannot and ignore it)
 * @since 2.17.0
 * @see DatabaseConfiguration
 * @see DatabaseSupport#supportsBulkLoad()
 */
public record BulkLoadStrategy(Mode mode, boolean revalidate) {

    /** The available ordering modes. */
    public enum Mode {
        /** Respect the topological level barrier; never disable constraints (the default). */
        ORDERED,
        /** Disable foreign-key enforcement, fill all tables at once with no barrier, then re-enable. */
        UNORDERED_BULK
    }

    /**
     * Validates the mode.
     *
     * @throws IllegalArgumentException if {@code mode} is null
     */
    public BulkLoadStrategy {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
    }

    /**
     * The back-compatible default: fill in foreign-key dependency order, never disabling constraints.
     *
     * @return an {@link Mode#ORDERED} strategy
     */
    public static BulkLoadStrategy ordered() {
        return new BulkLoadStrategy(Mode.ORDERED, false);
    }

    /**
     * Disable foreign-key enforcement, fill every table concurrently with no topological barrier, then
     * re-enable enforcement without re-validating (safe because the data is consistent by construction).
     *
     * @return an {@link Mode#UNORDERED_BULK} strategy
     */
    public static BulkLoadStrategy unorderedBulk() {
        return new BulkLoadStrategy(Mode.UNORDERED_BULK, false);
    }

    /**
     * Whether this strategy is {@link Mode#UNORDERED_BULK}.
     *
     * @return {@code true} for unordered bulk loading
     */
    public boolean isUnordered() {
        return mode == Mode.UNORDERED_BULK;
    }
}
