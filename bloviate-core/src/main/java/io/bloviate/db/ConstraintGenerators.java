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

import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.DoubleGenerator;
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.LongGenerator;
import io.bloviate.gen.ScaledBigDecimalGenerator;
import io.bloviate.gen.WeightedCategoricalGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;

/**
 * Builds a generator that satisfies a {@link ColumnConstraint}: a categorical generator over the
 * allowed values, or a numeric generator bounded to the constraint's range. Returns {@code null} when
 * the constraint cannot be honored for the column's type, so the caller falls back to the type default.
 *
 * @since 2.14.0
 */
final class ConstraintGenerators {

    private ConstraintGenerators() {
    }

    static DataGenerator<?> create(Column column, ColumnConstraint constraint, RandomGenerator random) {
        if (constraint.hasAllowedValues()) {
            WeightedCategoricalGenerator.Builder<String> builder = new WeightedCategoricalGenerator.Builder<>(random);
            for (String value : constraint.allowedValues()) {
                builder.add(value, 1.0);
            }
            return builder.build();
        }
        if (constraint.hasBoundedRange()) {
            return range(column, constraint, random);
        }
        return null;
    }

    private static DataGenerator<?> range(Column column, ColumnConstraint constraint, RandomGenerator random) {
        if (column.jdbcType() == null) {
            return null;
        }
        return switch (column.jdbcType()) {
            case TINYINT, SMALLINT, INTEGER -> {
                long start = lowerBound(constraint);
                long endExclusive = upperBound(constraint) + 1;
                yield endExclusive <= start ? null
                        : new IntegerGenerator.Builder(random).start((int) start).end((int) endExclusive).build();
            }
            case BIGINT -> {
                long start = lowerBound(constraint);
                long endExclusive = upperBound(constraint) + 1;
                yield endExclusive <= start ? null
                        : new LongGenerator.Builder(random).start(start).end(endExclusive).build();
            }
            case NUMERIC, DECIMAL -> {
                int scale = column.maxDigits() != null ? column.maxDigits() : 2;
                yield new ScaledBigDecimalGenerator.Builder(random)
                        .start(constraint.min().doubleValue()).end(constraint.max().doubleValue()).scale(scale).build();
            }
            case REAL, FLOAT, DOUBLE -> new DoubleGenerator.Builder(random)
                    .start(constraint.min().doubleValue()).end(constraint.max().doubleValue()).build();
            default -> null;
        };
    }

    /** Smallest integer the range admits (ceil of an inclusive min, or floor+1 of an exclusive min). */
    private static long lowerBound(ColumnConstraint constraint) {
        BigDecimal min = constraint.min();
        return constraint.minInclusive()
                ? min.setScale(0, RoundingMode.CEILING).longValue()
                : min.setScale(0, RoundingMode.FLOOR).longValue() + 1;
    }

    /** Largest integer the range admits (floor of an inclusive max, or ceil-1 of an exclusive max). */
    private static long upperBound(ColumnConstraint constraint) {
        BigDecimal max = constraint.max();
        return constraint.maxInclusive()
                ? max.setScale(0, RoundingMode.FLOOR).longValue()
                : max.setScale(0, RoundingMode.CEILING).longValue() - 1;
    }
}
