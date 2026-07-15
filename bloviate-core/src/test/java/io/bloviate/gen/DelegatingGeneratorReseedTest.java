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
import java.util.function.Function;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Generators that hold delegate generators must rebuild them on {@link DataGenerator#reseed(long)}
 * (via the {@code onReseed()} hook): two instances constructed from <em>different</em> sources and
 * then reseeded identically must produce identical sequences. Before the hook existed the delegates
 * kept drawing from the construction-time source, so foreign-key replay backed by such a generator
 * diverged from the parent sequence it was meant to track.
 */
class DelegatingGeneratorReseedTest {

    private static <T> void assertReseedConverges(Function<RandomGenerator, DataGenerator<T>> factory) {
        DataGenerator<T> first = factory.apply(new Random(1));
        DataGenerator<T> second = factory.apply(new Random(2));

        // establish divergent internal state before reseeding
        first.generate();
        second.generate();
        second.generate();

        first.reseed(99L);
        second.reseed(99L);

        for (int i = 0; i < 25; i++) {
            assertEquals(first.generate(), second.generate(),
                    "sequences must be identical after an identical reseed");
        }
    }

    @Test
    void reseedReachesDelegates() {
        assertReseedConverges(r -> new BitGenerator.Builder(r).build());
        assertReseedConverges(r -> new BitStringGenerator.Builder(r).size(8).build());
        assertReseedConverges(r -> new CharacterGenerator.Builder(r).build());
        assertReseedConverges(r -> new CidrGenerator.Builder(r).build());
        assertReseedConverges(r -> new DateGenerator.Builder(r).build());
        assertReseedConverges(r -> new InetGenerator.Builder(r).build());
        assertReseedConverges(r -> new InstantGenerator.Builder(r).build());
        assertReseedConverges(r -> new IntervalGenerator.Builder(r).build());
        assertReseedConverges(r -> new MacAddressGenerator.Builder(r).build());
        assertReseedConverges(r -> new ShortGenerator.Builder(r).build());
        assertReseedConverges(r -> new SqlDateGenerator.Builder(r).build());
        assertReseedConverges(r -> new SqlTimeGenerator.Builder(r).build());
        assertReseedConverges(r -> new SqlTimestampGenerator.Builder(r).build());
        assertReseedConverges(r -> new XmlGenerator.Builder(r).build());
        assertReseedConverges(r -> new BigDecimalGenerator.Builder(r).build());
    }
}
