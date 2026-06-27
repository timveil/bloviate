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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigDecimalGeneratorTest {

    private static BigDecimal generate(Integer precision, Integer digits) {
        return new BigDecimalGenerator.Builder(new Random())
                .precision(precision)
                .digits(digits)
                .build()
                .generate();
    }

    @Test
    void rejectsScaleLargerThanPrecision() {
        assertThrows(IllegalArgumentException.class, () -> generate(5, 10));
    }

    @Test
    void handlesLargePrecisionWithLargeScale() {
        // previously threw: precision is capped to 25 internally but the un-capped scale
        // produced a negative integer-part length, tripping range validation
        for (int i = 0; i < 100; i++) {
            BigDecimal value = generate(30, 28);
            assertNotNull(value);
            assertTrue(value.scale() <= 25, "scale should be capped to the reduced precision: " + value);
            assertTrue(value.precision() <= 25, "precision should be capped: " + value);
        }
    }

    @Test
    void handlesScaleEqualToPrecision() {
        // all digits to the right of the decimal point
        for (int i = 0; i < 100; i++) {
            BigDecimal value = generate(10, 10);
            assertNotNull(value);
            assertEquals(BigDecimal.ZERO, value.setScale(0, java.math.RoundingMode.DOWN),
                    "value should be purely fractional: " + value);
        }
    }

    @Test
    void capsEnormousPrecisionFromMetadata() {
        // CRDB reports precision values like 131089; the generator must not blow up
        for (int i = 0; i < 100; i++) {
            BigDecimal value = generate(131089, 0);
            assertNotNull(value);
            assertTrue(value.precision() <= 25, "precision should be capped to 25: " + value);
        }
    }

    @Test
    void fallsBackToDoubleWhenPrecisionUnset() {
        assertNotNull(generate(null, null));
    }
}
