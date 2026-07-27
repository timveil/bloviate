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

import java.util.random.RandomGenerator;

/**
 * Emits a parent table's "number of children" column under variable parent-child
 * cardinality: for the k-th row (0-based) it returns
 * {@link ChildCardinality#count(long) cardinality.count(k)} (for example TPC-C's
 * {@code o_ol_cnt}). Sharing the same {@link ChildCardinality} instance with the child
 * table's {@link ChildKeyComponentGenerator}s guarantees that each parent's declared
 * child count equals the number of child rows generated for it.
 *
 * <p>The produced values do not depend on the random source; counter state is held by
 * the generator instance and advances on every {@link #generate()}.
 */
public class ChildCountGenerator extends AbstractIndexedIntegerGenerator {

    private final ChildCardinality cardinality;

    /** The value is {@code cardinality.count(rowIndex)} &mdash; a pure function of the row index. */
    @Override
    protected Integer valueAt(long rowIndex) {
        return cardinality.count(rowIndex);
    }

    /** Builder for {@link ChildCountGenerator}. */
    public static class Builder extends AbstractBuilder<Integer> {

        private ChildCardinality cardinality;

        /**
         * Creates a builder.
         *
         * @param random the random source (unused by this generator, but required by the contract)
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * The {@link ChildCardinality} consulted for each parent row: row {@code k} emits
         * {@link ChildCardinality#count(long) cardinality.count(k)}. To keep this column honest, it
         * <b>must be the same {@link ChildCardinality} instance</b> shared with the child table's
         * {@link ChildKeyComponentGenerator}s, so each parent's declared child count equals the number
         * of child rows actually generated for it. Required — {@link #build()} throws if left unset.
         *
         * @param cardinality the shared per-parent child-count source
         * @return this builder, for chaining
         */
        public Builder cardinality(ChildCardinality cardinality) {
            this.cardinality = cardinality;
            return this;
        }

        /**
         * Builds the generator.
         *
         * @return a new {@link ChildCountGenerator}
         * @throws IllegalStateException if no {@link #cardinality(ChildCardinality) cardinality} was set
         */
        @Override
        public ChildCountGenerator build() {
            return new ChildCountGenerator(this);
        }
    }

    private ChildCountGenerator(Builder builder) {
        super(builder.random);
        if (builder.cardinality == null) {
            throw new IllegalStateException("cardinality is required");
        }
        this.cardinality = builder.cardinality;
    }
}
