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

import java.sql.Timestamp;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentSqlTimestampGeneratorTest {

    @Test
    void generatesATimestampNearNow() {
        long before = System.currentTimeMillis();
        Timestamp value = new CurrentSqlTimestampGenerator.Builder(new Random()).build().generate();
        long after = System.currentTimeMillis();

        assertNotNull(value);
        assertTrue(value.getTime() >= before - 1000 && value.getTime() <= after + 1000,
                "timestamp not within expected window: " + value.getTime());
    }
}
