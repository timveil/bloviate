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

import java.math.BigDecimal;

public class BigDecimalGenerator implements DataGenerator<BigDecimal> {


    @Override
    public BigDecimal generate() {
        return BigDecimal.valueOf(new DoubleGenerator.Builder().build().generate());
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        public BigDecimalGenerator build() {
            return new BigDecimalGenerator(this);
        }
    }

    private BigDecimalGenerator(Builder builder) {

    }
}