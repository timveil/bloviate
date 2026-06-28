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

/**
 * How a {@link TableFiller} commits the rows it inserts.
 *
 * <p>By default ({@link #connectionDefault()}) the fill engine does not touch the connection's
 * transaction state at all — it relies on whatever autocommit the caller's connection has, which for
 * a typical autocommit connection means a commit per {@code executeBatch()}. Disabling autocommit and
 * committing less often cuts commit overhead and is usually faster.
 *
 * <ul>
 *   <li>{@link #connectionDefault()} — the engine leaves autocommit alone (back-compatible default).</li>
 *   <li>{@link #perTable()} — autocommit off, a single commit once the table is fully filled.</li>
 *   <li>{@link #everyNBatches(int)} — autocommit off, commit after every {@code n} JDBC batches
 *       (and once more for the trailing partial batch), bounding the size of an open transaction.</li>
 * </ul>
 *
 * <p>For anything other than {@link #connectionDefault()} the engine sets autocommit off for the fill,
 * rolls back on error, and restores the connection's prior autocommit setting when done.
 *
 * @param mode    the commit mode
 * @param batches the batch cadence for {@link Mode#EVERY_N_BATCHES} (ignored otherwise)
 * @since 2.10.0
 * @see DatabaseConfiguration
 */
public record CommitStrategy(Mode mode, int batches) {

    /** The available commit modes. */
    public enum Mode {
        /** Leave the connection's autocommit state untouched (the default, back-compatible behavior). */
        CONNECTION_DEFAULT,
        /** Autocommit off; commit once when the table is fully filled. */
        PER_TABLE,
        /** Autocommit off; commit after every {@code n} JDBC batches. */
        EVERY_N_BATCHES
    }

    /**
     * Validates the mode/batches pairing.
     *
     * @throws IllegalArgumentException if {@code mode} is null, or {@code batches < 1} for
     *                                  {@link Mode#EVERY_N_BATCHES}
     */
    public CommitStrategy {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode == Mode.EVERY_N_BATCHES && batches < 1) {
            throw new IllegalArgumentException("batches must be >= 1 for EVERY_N_BATCHES: " + batches);
        }
    }

    /**
     * The back-compatible default: the engine never changes the connection's autocommit state.
     *
     * @return a {@link Mode#CONNECTION_DEFAULT} strategy
     */
    public static CommitStrategy connectionDefault() {
        return new CommitStrategy(Mode.CONNECTION_DEFAULT, 0);
    }

    /**
     * Autocommit off, a single commit once the table is fully filled.
     *
     * @return a {@link Mode#PER_TABLE} strategy
     */
    public static CommitStrategy perTable() {
        return new CommitStrategy(Mode.PER_TABLE, 0);
    }

    /**
     * Autocommit off, commit after every {@code n} JDBC batches.
     *
     * @param n the number of batches between commits; must be {@code >= 1}
     * @return the strategy
     */
    public static CommitStrategy everyNBatches(int n) {
        return new CommitStrategy(Mode.EVERY_N_BATCHES, n);
    }

    /**
     * Whether the engine manages the transaction (i.e. anything other than {@link Mode#CONNECTION_DEFAULT}).
     *
     * @return whether the engine drives autocommit and commits/rolls back itself
     */
    public boolean managesTransaction() {
        return mode != Mode.CONNECTION_DEFAULT;
    }
}
