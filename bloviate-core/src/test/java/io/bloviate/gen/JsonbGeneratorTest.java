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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonbGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void generatesValidJsonObjectWithRequestedFieldCount() throws Exception {
        JsonbGenerator generator = new JsonbGenerator.Builder(new Random()).fields(5).build();

        for (int i = 0; i < 100; i++) {
            String json = generator.generate();
            assertNotNull(json);

            JsonNode node = MAPPER.readTree(json);
            assertTrue(node.isObject(), "expected a JSON object: " + json);
            assertEquals(5, node.size(), "unexpected field count: " + json);
        }
    }

    @Test
    void defaultsToThreeFields() throws Exception {
        JsonbGenerator generator = new JsonbGenerator.Builder(new Random()).build();

        JsonNode node = MAPPER.readTree(generator.generate());
        assertEquals(3, node.size());
    }

    @Test
    void isDeterministicForAGivenSeed() {
        JsonbGenerator a = new JsonbGenerator.Builder(RandomGenerators.create(42L)).fields(4).build();
        JsonbGenerator b = new JsonbGenerator.Builder(RandomGenerators.create(42L)).fields(4).build();

        assertEquals(a.generate(), b.generate());
    }
}
