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
            int precision = Math.min(maxPrecision, 25);
            int scale = (maxDigits != null ? maxDigits : 0);

            StringJoiner joiner = new StringJoiner(".");
            joiner.add(RandomStringUtils.randomNumeric(1, (precision - scale) + 1));

            if (scale > 0) {
                joiner.add(RandomStringUtils.randomNumeric(1, scale + 1));
            }

            String bigDecimalString = joiner.toString();

            logger.debug("precision {}, scale {}, bd {}", precision, scale, bigDecimalString);

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
