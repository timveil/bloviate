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

package io.bloviate.db;

import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.NormalIntegerGenerator;
import io.bloviate.gen.SkewedTimestampGenerator;
import io.bloviate.gen.WeightedCategoricalGenerator;
import io.bloviate.gen.ZipfianIntegerGenerator;
import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the {@link Distributions} convenience factories build the expected generators from the
 * engine-supplied seeded {@link java.util.random.RandomGenerator}, exactly as a per-column override
 * does — so a column can opt into a distribution without writing a factory.
 */
class DistributionsTest {

    @Test
    void factoriesBuildTheExpectedGeneratorTypes() {
        var random = RandomGenerators.create(1L);
        assertInstanceOf(WeightedCategoricalGenerator.class, Distributions.weighted(Map.of("A", 0.9, "B", 0.1)).create(random));
        assertInstanceOf(NormalIntegerGenerator.class, Distributions.normalInt(5, 1, 1, 10).create(random));
        assertInstanceOf(ZipfianIntegerGenerator.class, Distributions.zipfian(1000).create(random));
        assertInstanceOf(SkewedTimestampGenerator.class, Distributions.recentTimestamps().create(random));
        assertNotNull(Distributions.normal(0, 1, -1, 1).create(random));
    }

    @Test
    void wiredFactoryIsSeededByTheEngineAndReproducible() {
        ColumnGeneratorFactory factory = Distributions.zipfian(1000);
        DataGenerator<?> a = factory.create(RandomGenerators.create(7L));
        DataGenerator<?> b = factory.create(RandomGenerators.create(7L));
        for (int i = 0; i < 500; i++) {
            assertEquals(a.generate(), b.generate(), "engine-seeded factory must be reproducible at draw " + i);
        }
    }
}
