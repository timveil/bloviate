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
import java.util.StringJoiner;

public class BigDecimalGenerator implements DataGenerator<BigDecimal> {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final Integer maxPrecision;
    private final Integer maxDigits;

    @Override
    public BigDecimal generate() {

        if (maxPrecision != null) {

            // maxPrecision can be enormous (131089 for CRDB) and not helpful for testing therefore we significantly reduce
            int precision = Math.min(maxPrecision, 25) - (maxDigits != null ? maxDigits : 0);

            StringJoiner joiner = new StringJoiner(".");

            // generate random numeric string then strip leading zeros.  if this results in empty string, default to "1"
            String stripped = StringUtils.stripStart(RandomStringUtils.randomNumeric(1, (precision) + 1), "0");

            if (stripped.isEmpty()) {
                stripped = "1";
            }

            joiner.add(stripped);

            if (maxDigits != null) {
                joiner.add(RandomStringUtils.randomNumeric(1, maxDigits + 1));
            }

            String bigDecimalString = joiner.toString();

            logger.debug("maxPrecision [{}], adjustedPrecision [{}],  maxDigits [{}], bigDecimal [{}]", maxPrecision, precision, maxDigits, bigDecimalString);

            return new BigDecimal(bigDecimalString);
        } else {
            return BigDecimal.valueOf(new DoubleGenerator.Builder().build().generate());
        }

    }

    @Override
    public String generateAsString() {
        return generate().toString();
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
