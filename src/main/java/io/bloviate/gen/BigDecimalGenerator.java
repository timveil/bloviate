/*
 * Copyright 2020 Tim Veil
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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BigDecimalGenerator extends AbstractDataGenerator<BigDecimal> {

    final Logger logger = LoggerFactory.getLogger(getClass());

    // this is the number of digits on both sides of the decimal point, can be null
    private final Integer maxPrecision;

    // digits to right of decimal point (fractional digits), can be null
    private final Integer maxDigits;

    @Override
    public BigDecimal generate() {

        if (maxPrecision != null) {

            // maxPrecision can be enormous (131089 for CRDB) and not helpful for testing therefore we significantly reduce
            int minMaxPrecision = Math.min(maxPrecision, 25);

            if (maxDigits != null && maxDigits > maxPrecision) {
                throw new IllegalArgumentException("max digits cannot be larger than max precision");
            } else if (maxDigits != null && maxDigits.equals(maxPrecision)) {
                // if these are equal then all digits are to the right of the decimal place
                String bigDecimalString = "." + RandomStringUtils.randomNumeric(1, maxDigits + 1);

                return new BigDecimal(bigDecimalString);
            } else {

                if (maxDigits != null) {

                    int adjustedPrecision = minMaxPrecision - maxDigits;

                    // generate random numeric string then strip leading zeros.  if this results in empty string, default to "1"
                    String left = StringUtils.stripStart(RandomStringUtils.randomNumeric(1, (adjustedPrecision) + 1), "0");

                    if (left.isEmpty()) {
                        left = "1";
                    }

                    String right = RandomStringUtils.randomNumeric(1, maxDigits + 1);

                    String bigDecimalString = left + "." + right;

                    return new BigDecimal(bigDecimalString);
                } else {
                    return new BigDecimal(RandomStringUtils.randomNumeric(1, minMaxPrecision + 1));
                }
            }

        } else {
            return BigDecimal.valueOf(new DoubleGenerator.Builder().build().generate());
        }

    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        statement.setBigDecimal(parameterIndex, generate());
    }

    public static class Builder {

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

        public BigDecimalGenerator build() {
            return new BigDecimalGenerator(this);
        }
    }

    private BigDecimalGenerator(Builder builder) {
        this.maxDigits = builder.maxDigits;
        this.maxPrecision = builder.maxPrecision;
    }
}
