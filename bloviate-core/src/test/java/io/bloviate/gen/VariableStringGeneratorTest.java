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

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableStringGeneratorTest {

    @Test
    void lengthStaysWithinInclusiveBoundsAndUsesLettersByDefault() {
        VariableStringGenerator generator = new VariableStringGenerator.Builder(new Random()).minLength(5).maxLength(10).build();

        for (int i = 0; i < 500; i++) {
            String value = generator.generate();
            assertTrue(value.length() >= 5 && value.length() <= 10, "unexpected length: " + value.length());
            assertTrue(value.chars().allMatch(Character::isLetter), "expected letters only: " + value);
        }
    }

    @Test
    void numbersOnlyProducesDigits() {
        // regression for the numbers() builder bug (this.numbers = letters)
        VariableStringGenerator generator = new VariableStringGenerator.Builder(new Random())
                .minLength(8).maxLength(8).letters(false).numbers(true).build();

        for (int i = 0; i < 500; i++) {
            String value = generator.generate();
            assertEquals(8, value.length());
            assertTrue(value.chars().allMatch(Character::isDigit), "expected digits only: " + value);
        }
    }

    @Test
    void lettersAndNumbersProducesAlphanumeric() {
        VariableStringGenerator generator = new VariableStringGenerator.Builder(new Random())
                .minLength(20).maxLength(20).letters(true).numbers(true).build();

        for (int i = 0; i < 500; i++) {
            String value = generator.generate();
            assertEquals(20, value.length());
            assertTrue(value.chars().allMatch(Character::isLetterOrDigit), "expected alphanumeric: " + value);
        }
    }
}
