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

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.random.RandomGenerator;

/**
 * Generates {@link BigDecimal} values sized to a column's declared precision and scale using
 * the seeded random source, so a given seed yields a reproducible sequence. When a precision
 * is configured the value has up to {@code maxPrecision} total significant digits with
 * {@code maxDigits} of them to the right of the decimal point; both bounds are capped at
 * {@code 25} to keep test values manageable (some databases report enormous precision). When
 * no precision is configured the value falls back to one produced by a {@link DoubleGenerator}.
 * For a value constrained to an explicit numeric range and fixed scale see
 * {@link ScaledBigDecimalGenerator}; for a fixed value see {@link StaticBigDecimalGenerator}.
 */
public class BigDecimalGenerator extends AbstractDataGenerator<BigDecimal> {

    // this is the number of digits on both sides of the decimal point, can be null
    private final Integer maxPrecision;

    // rebuilt on reseed(): the previous code constructed one per generate() call from the
    // current random source, so a reseed must reach the delegate for FK replay to track the
    // parent sequence
    private DoubleGenerator doubleGenerator;

    // digits to right of decimal point (fractional digits), can be null
    private final Integer maxDigits;

    @Override
    public BigDecimal generate() {

        if (maxPrecision != null) {

            if (maxDigits != null && maxDigits > maxPrecision) {
                throw new IllegalArgumentException("max digits cannot be larger than max precision");
            }

            // maxPrecision can be enormous (131089 for CRDB) and not helpful for testing therefore we significantly reduce.
            // the scale must be capped to the same bound so the integer part never ends up with a negative length.
            int precision = Math.min(maxPrecision, 25);
            int scale = maxDigits == null ? 0 : Math.min(maxDigits, precision);
            int integerDigits = precision - scale;

            if (integerDigits == 0) {
                // all digits are to the right of the decimal point
                return new BigDecimal("." + randomUtils.randomNumeric(1, scale + 1));
            }

            // generate random numeric string then strip leading zeros.  if this results in empty string, default to "1"
            String left = StringUtils.stripStart(randomUtils.randomNumeric(1, integerDigits + 1), "0");

            if (left.isEmpty()) {
                left = "1";
            }

            if (scale == 0) {
                return new BigDecimal(left);
            }

            String right = randomUtils.randomNumeric(1, scale + 1);

            return new BigDecimal(left + "." + right);

        } else {
            return BigDecimal.valueOf(doubleGenerator.generate());
        }

    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, BigDecimal value) throws SQLException {
        statement.setBigDecimal(parameterIndex, value);
    }

    @Override
    public BigDecimal get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getBigDecimal(columnIndex);
    }

    /** Fluent builder for {@link BigDecimalGenerator}. */
    public static class Builder extends AbstractBuilder<BigDecimal> {

        private Integer maxPrecision;
        private Integer maxDigits;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets the maximum total precision: the number of significant digits on both sides of the
         * decimal point. May be {@code null} (the default), in which case the value falls back to a
         * {@link DoubleGenerator} result. When set, the effective precision is capped at {@code 25}.
         *
         * @param maxPrecision the maximum total number of significant digits, or {@code null} for none
         * @return this builder, for chaining
         */
        public Builder precision(Integer maxPrecision) {
            this.maxPrecision = maxPrecision;
            return this;
        }

        /**
         * Sets the maximum scale: the number of fractional digits to the right of the decimal point.
         * May be {@code null} (the default), which is treated as a scale of {@code 0}. The effective
         * scale is capped at the effective precision, and must not exceed the configured precision or
         * {@link #build()} will produce a generator that throws at generation time.
         *
         * @param maxDigits the maximum number of fractional digits, or {@code null} for none
         * @return this builder, for chaining
         */
        public Builder digits(Integer maxDigits) {
            this.maxDigits = maxDigits;
            return this;
        }

        @Override
        public BigDecimalGenerator build() {
            return new BigDecimalGenerator(this);
        }
    }

    @Override
    public void reseed(long seed) {
        super.reseed(seed);
        this.doubleGenerator = new DoubleGenerator.Builder(random).build();
    }

    private BigDecimalGenerator(Builder builder) {
        super(builder.random);
        this.maxDigits = builder.maxDigits;
        this.maxPrecision = builder.maxPrecision;
        // shared with the unbounded path: constructing it draws nothing, and reusing it avoids
        // three allocations per generated value (same draw sequence as building one per call)
        this.doubleGenerator = new DoubleGenerator.Builder(builder.random).build();
    }
}
