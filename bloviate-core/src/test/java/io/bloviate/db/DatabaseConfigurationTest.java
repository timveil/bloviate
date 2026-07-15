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

import io.bloviate.ext.DefaultSupport;
import io.bloviate.ext.GeneratorRegistry;
import io.bloviate.gen.IntegerGenerator;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigurationTest {

    @Test
    void backCompatConstructorHasNoGeneratorRegistry() {
        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 100, new DefaultSupport(), null);

        assertNull(configuration.generatorRegistry());
    }

    @Test
    void builderProducesSameConfigurationAsCanonicalConstructor() {
        DefaultSupport support = new DefaultSupport();
        GeneratorRegistry registry = new GeneratorRegistry.Builder().build();

        DatabaseConfiguration built = new DatabaseConfiguration.Builder(128, 100, support)
                .generatorRegistry(registry)
                .seed(42L)
                .commitStrategy(CommitStrategy.perTable())
                .bulkLoadStrategy(BulkLoadStrategy.unorderedBulk())
                .build();

        assertEquals(new DatabaseConfiguration(128, 100, support, null, registry, 42L,
                CommitStrategy.perTable(), BulkLoadStrategy.unorderedBulk()), built);
    }

    @Test
    void builderAppliesDefaults() {
        DatabaseConfiguration built = new DatabaseConfiguration.Builder(128, 100, new DefaultSupport()).build();

        assertEquals(0L, built.seed());
        assertNull(built.tableConfigurations());
        assertNull(built.generatorRegistry());
        assertEquals(CommitStrategy.connectionDefault(), built.commitStrategy());
        assertEquals(BulkLoadStrategy.ordered(), built.bulkLoadStrategy());
    }

    @Test
    void builderValidatesInputs() {
        assertThrows(NullPointerException.class,
                () -> new DatabaseConfiguration.Builder(128, 100, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfiguration.Builder(0, 100, new DefaultSupport()).build());
    }

    @Test
    void registryIsRetained() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerJdbcType(JDBCType.INTEGER, (column, random) -> new IntegerGenerator.Builder(random).build())
                .build();

        DatabaseConfiguration configuration = new DatabaseConfiguration(128, 100, new DefaultSupport(), null, registry);

        assertSame(registry, configuration.generatorRegistry());
    }

    @Test
    void rejectsZeroBatchSize() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfiguration(0, 100, new DefaultSupport(), null));

        assertEquals("batchSize must be >= 1: 0", e.getMessage());
    }

    @Test
    void rejectsNegativeBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfiguration(-5, 100, new DefaultSupport(), null));
    }
}
