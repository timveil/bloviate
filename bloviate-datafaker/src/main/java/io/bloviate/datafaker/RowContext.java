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

import io.bloviate.db.ColumnGeneratorFactory;

import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * Materializes one coherent <em>entity</em> per row (a {@link Person}, a geo tuple, …) and lets
 * several columns project fields out of it, so correlated columns agree — issue #473 "referential
 * realism".
 *
 * <p>This is how Bloviate keeps cross-column consistency without breaking its two core invariants.
 * The entity for a row is a <strong>pure function of {@code (groupSeed, rowIndex)}</strong> (supplied
 * as the {@code entityFactory}), so it is the same no matter the order columns are generated or how
 * the table is partitioned across threads — sequential and parallel fills produce identical data. The
 * result is cached per thread for the current row, so the participating columns of a row materialize
 * the entity once between them, not once each.
 *
 * <p>Wire correlated columns by sharing <em>one</em> context and registering each as a
 * {@link #project(Function) projection}:
 *
 * <pre>{@code
 * RowContext<Person> person = People.context(seed, Locale.ENGLISH);
 * Set.of(
 *     new ColumnConfiguration("first_name", person.project(Person::firstName)),
 *     new ColumnConfiguration("last_name",  person.project(Person::lastName)),
 *     new ColumnConfiguration("email",      person.project(Person::email)));   // jane.doe…@example.com
 * }</pre>
 *
 * @param <E> the entity type projected by the participating columns
 * @since 2.12.0
 * @see People
 * @see RowProjectionGenerator
 */
public final class RowContext<E> {

    private final LongFunction<E> entityFactory;
    // per-thread, single-row cache: under intra-table partitioning several threads share this one
    // context, so the cache must not be shared mutable state — ThreadLocal keeps each worker isolated
    private final ThreadLocal<Memo<E>> memo = ThreadLocal.withInitial(Memo::new);

    /**
     * @param entityFactory a pure mapping from row index to the row's entity; must depend only on the
     *                      row index (and a fixed seed it closes over) so output is order-independent
     */
    public RowContext(LongFunction<E> entityFactory) {
        this.entityFactory = entityFactory;
    }

    /**
     * Returns the entity for the given absolute row index, computing it at most once per row per
     * thread.
     *
     * @param rowIndex the absolute, 0-based row index
     * @return the row's entity
     */
    public E at(long rowIndex) {
        Memo<E> current = memo.get();
        if (!current.valid || current.rowIndex != rowIndex) {
            current.entity = entityFactory.apply(rowIndex);
            current.rowIndex = rowIndex;
            current.valid = true;
        }
        return current.entity;
    }

    /**
     * A {@link ColumnGeneratorFactory} that projects one field of this context's entity for each row.
     * All projections of the same context stay in lockstep and consistent. The engine-supplied
     * per-column {@code random} is intentionally ignored — the value comes from the shared entity.
     *
     * @param selector picks the field to emit (e.g. {@code Person::email})
     * @return a factory wireable through {@code ColumnConfiguration}
     */
    public ColumnGeneratorFactory project(Function<E, String> selector) {
        return random -> new RowProjectionGenerator<>(random, this, selector);
    }

    private static final class Memo<E> {
        private long rowIndex;
        private boolean valid;
        private E entity;
    }
}
