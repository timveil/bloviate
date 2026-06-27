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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaledBigDecimalGeneratorTest {

    @Test
    void staysWithinRangeAndScale() {
        ScaledBigDecimalGenerator generator = new ScaledBigDecimalGenerator.Builder(new Random(42))
                .start(0.0).end(0.2).scale(4).build();

        BigDecimal low = new BigDecimal("0.0000");
        BigDecimal high = new BigDecimal("0.2000");

        for (int i = 0; i < 5000; i++) {
            BigDecimal value = generator.generate();
            assertEquals(4, value.scale(), "unexpected scale: " + value);
            assertTrue(value.compareTo(low) >= 0 && value.compareTo(high) <= 0, "out of range: " + value);
        }
    }

    @Test
    void honoursScaleTwoWithinMonetaryRange() {
        ScaledBigDecimalGenerator generator = new ScaledBigDecimalGenerator.Builder(new Random(7))
                .start(0.01).end(9999.99).scale(2).build();

        BigDecimal high = new BigDecimal("9999.99");

        for (int i = 0; i < 5000; i++) {
            BigDecimal value = generator.generate();
            assertEquals(2, value.scale(), "unexpected scale: " + value);
            assertTrue(value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(high) <= 0, "out of range: " + value);
        }
    }

    @Test
    void isDeterministicForAFixedSeed() {
        ScaledBigDecimalGenerator first = new ScaledBigDecimalGenerator.Builder(new Random(99)).start(1.0).end(100.0).scale(2).build();
        ScaledBigDecimalGenerator second = new ScaledBigDecimalGenerator.Builder(new Random(99)).start(1.0).end(100.0).scale(2).build();
        assertEquals(first.generate(), second.generate());
    }
}
