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

import io.bloviate.util.SeededRandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public class BigDecimalGenerator extends AbstractDataGenerator<BigDecimal> {

    // this is the number of digits on both sides of the decimal point, can be null
    private final Integer maxPrecision;

    // digits to right of decimal point (fractional digits), can be null
    private final Integer maxDigits;

    private final DoubleGenerator doubleGenerator;

    @Override
    public BigDecimal generate(Random random) {

        if (maxPrecision != null) {

            SeededRandomUtils randomUtils = new SeededRandomUtils(random);

            // maxPrecision can be enormous (131089 for CockroachDB) and not helpful for testing therefore we significantly reduce
            int minMaxPrecision = Math.min(maxPrecision, 25);

            if (maxDigits != null && maxDigits > maxPrecision) {
                throw new IllegalArgumentException("max digits cannot be larger than max precision");
            } else if (maxDigits != null && maxDigits.equals(maxPrecision)) {
                // if these are equal then all digits are to the right of the decimal place
                String bigDecimalString = "." + randomUtils.randomNumeric(1, maxDigits + 1);

                return new BigDecimal(bigDecimalString);
            } else {

                if (maxDigits != null) {

                    int adjustedPrecision = minMaxPrecision - maxDigits;

                    // generate random numeric string then strip leading zeros.  if this results in empty string, default to "1"
                    String left = StringUtils.stripStart(randomUtils.randomNumeric(1, (adjustedPrecision) + 1), "0");

                    if (left.isEmpty()) {
                        left = "1";
                    }

                    String right = randomUtils.randomNumeric(1, maxDigits + 1);

                    String bigDecimalString = left + "." + right;

                    return new BigDecimal(bigDecimalString);
                } else {
                    return new BigDecimal(randomUtils.randomNumeric(1, minMaxPrecision + 1));
                }
            }

        } else {
            return BigDecimal.valueOf(doubleGenerator.generate(random));
        }

    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setBigDecimal(parameterIndex, (BigDecimal) value);
    }


    public static class Builder implements io.bloviate.gen.Builder {

        private Integer maxPrecision;
        private Integer maxDigits;

        public Builder precision(Integer maxPrecision) {
            this.maxPrecision = maxPrecision;
            return this;
        }

        public Builder digits(Integer maxDigits) {
            this.maxDigits = maxDigits;
            return this;
        }

        @Override
        public BigDecimalGenerator build() {
            return new BigDecimalGenerator(this);
        }
    }

    private BigDecimalGenerator(Builder builder) {
        this.maxDigits = builder.maxDigits;
        this.maxPrecision = builder.maxPrecision;
        this.doubleGenerator = new DoubleGenerator.Builder().maxDigits(maxDigits).build();
    }
}
