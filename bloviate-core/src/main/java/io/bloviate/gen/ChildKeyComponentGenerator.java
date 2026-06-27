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

/**
 * Emits one component of a child table's composite key when each parent has a
 * <em>variable</em> number of children (see {@link ChildCardinality}), which the
 * fixed-cardinality {@link CompositeKeyComponentGenerator} cannot express.
 *
 * <p>The child key is {@code (parent-key-components..., sequence)}: every child row
 * repeats its parent's key and adds a 1-based sequence number within the parent. A
 * generator instance emits a single one of those components, in one of two modes:
 * <ul>
 *   <li><b>parent component</b> — the value of one dimension of the parent's key.
 *       Computed from the parent's global index with the same
 *       {@code start + ((parentIndex / repeat) % cycle)} formula used by the parent's own
 *       {@link CompositeKeyComponentGenerator}, so the values line up exactly.</li>
 *   <li><b>sequence</b> — the child's position within its parent, as
 *       {@code start + positionWithinParent} (so {@code start = 1} yields {@code 1, 2, 3, ...}).</li>
 * </ul>
 *
 * <p>Each instance walks the parents incrementally with a private cursor
 * {@code (parentIndex, childrenRemaining, position)}, advancing by one child on every
 * {@link #generate()} and re-reading the next parent's child count from the shared
 * {@link ChildCardinality} when the current parent is exhausted. Because the cardinality is
 * deterministic and each key column is generated exactly once per row, the separate
 * generators for the different key components advance in lockstep and produce a consistent
 * child-key tuple per row, with no shared mutable state. Parents with a zero child count are
 * skipped.
 *
 * <p>The produced values do not depend on the random source.
 */
public class ChildKeyComponentGenerator extends AbstractDataGenerator<Integer> {

    private final ChildCardinality cardinality;
    private final boolean sequence;
    private final int start;
    private final long repeat;
    private final int cycle;

    // private walk cursor; generation for a single table is sequential, but guard anyway
    private long parentIndex = -1;
    private int childrenRemaining = 0;
    private int position = 0;

    @Override
    public synchronized Integer generate() {
        if (childrenRemaining == 0) {
            do {
                parentIndex++;
                childrenRemaining = cardinality.count(parentIndex);
            } while (childrenRemaining == 0);
            position = 0;
        }
        int currentPosition = position; // 0-based position within the current parent
        childrenRemaining--;
        position++;

        if (sequence) {
            return start + currentPosition;
        }
        return start + (int) ((parentIndex / repeat) % cycle);
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

        private ChildCardinality cardinality;
        private boolean sequence = false;
        private int start = 1;
        private long repeat = 1;
        private int cycle = 1;

        public Builder(RandomGenerator random) {
            super(random);
        }

        public Builder cardinality(ChildCardinality cardinality) {
            this.cardinality = cardinality;
            return this;
        }

        /**
         * Emit the child's 1-based sequence number within its parent (using {@code start}
         * as the base). Mutually exclusive with {@link #repeat(long)}/{@link #cycle(int)}.
         */
        public Builder sequence() {
            this.sequence = true;
            return this;
        }

        public Builder start(int start) {
            this.start = start;
            return this;
        }

        /**
         * For a parent-component generator, the number of consecutive parents that share this
         * dimension's value — i.e. the product of the sizes of all parent dimensions nested
         * inside this one (mirrors {@link CompositeKeyComponentGenerator}).
         */
        public Builder repeat(long repeat) {
            this.repeat = repeat;
            return this;
        }

        /**
         * For a parent-component generator, the size of this parent dimension.
         */
        public Builder cycle(int cycle) {
            this.cycle = cycle;
            return this;
        }

        @Override
        public ChildKeyComponentGenerator build() {
            return new ChildKeyComponentGenerator(this);
        }
    }

    private ChildKeyComponentGenerator(Builder builder) {
        super(builder.random);
        if (builder.cardinality == null) {
            throw new IllegalStateException("cardinality is required");
        }
        this.cardinality = builder.cardinality;
        this.sequence = builder.sequence;
        this.start = builder.start;
        this.repeat = builder.repeat;
        this.cycle = builder.cycle;
    }
}
