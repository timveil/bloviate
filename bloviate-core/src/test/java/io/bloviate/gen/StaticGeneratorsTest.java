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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticGeneratorsTest {

    @Test
    void staticIntegerAlwaysReturnsConfiguredValue() {
        StaticIntegerGenerator generator = new StaticIntegerGenerator.Builder(new Random()).value(42).build();
        assertEquals(42, generator.generate().intValue());
        assertEquals(42, generator.generate().intValue());
    }

    @Test
    void staticDoubleAlwaysReturnsConfiguredValue() {
        StaticDoubleGenerator generator = new StaticDoubleGenerator.Builder(new Random()).value(3.5).build();
        assertEquals(3.5, generator.generate().doubleValue());
        assertEquals(3.5, generator.generate().doubleValue());
    }

    @Test
    void staticFloatAlwaysReturnsConfiguredValue() {
        StaticFloatGenerator generator = new StaticFloatGenerator.Builder(new Random()).value(2.5f).build();
        assertEquals(2.5f, generator.generate().floatValue());
        assertEquals(2.5f, generator.generate().floatValue());
    }

    @Test
    void staticStringAlwaysReturnsConfiguredValue() {
        StaticStringGenerator generator = new StaticStringGenerator.Builder(new Random()).value("CONSTANT").build();
        assertEquals("CONSTANT", generator.generate());
        assertEquals("CONSTANT", generator.generate());
    }

    @Test
    void staticBigDecimalAlwaysReturnsConfiguredValue() {
        StaticBigDecimalGenerator generator = new StaticBigDecimalGenerator.Builder(new Random()).value(new BigDecimal("300000.00")).build();
        assertEquals(new BigDecimal("300000.00"), generator.generate());
        assertEquals(new BigDecimal("300000.00"), generator.generate());
    }

    @Test
    void staticBigDecimalAcceptsStringValueAndPreservesScale() {
        StaticBigDecimalGenerator generator = new StaticBigDecimalGenerator.Builder(new Random()).value("-10.00").build();
        BigDecimal value = generator.generate();
        assertEquals(new BigDecimal("-10.00"), value);
        assertEquals(2, value.scale());
    }
}
