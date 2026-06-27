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

import io.bloviate.gen.IntegerGenerator;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ColumnConfigurationTest {

    private static ColumnConfiguration codeAlwaysSeven() {
        // start=7, end=8 (exclusive) -> always 7
        return new ColumnConfiguration("code", random -> new IntegerGenerator.Builder(random).start(7).end(8).build());
    }

    @Test
    void backCompatConstructorHasNoColumnConfigurations() {
        TableConfiguration configuration = new TableConfiguration("widget", 10);

        assertNull(configuration.columnConfigurations());
        assertNull(configuration.columnConfiguration("anything"));
    }

    @Test
    void columnConfigurationLookupIsCaseInsensitive() {
        ColumnConfiguration code = codeAlwaysSeven();
        TableConfiguration configuration = new TableConfiguration("widget", 10, Set.of(code));

        assertSame(code, configuration.columnConfiguration("code"));
        assertSame(code, configuration.columnConfiguration("CODE"));
        assertNull(configuration.columnConfiguration("label"));
    }

    @Test
    void factoryBuildsGeneratorFromSuppliedRandom() {
        ColumnConfiguration code = codeAlwaysSeven();

        assertEquals(7, code.generatorFactory().create(new Random(1)).generate());
        assertEquals(7, code.generatorFactory().create(new Random(999)).generate());
    }
}
