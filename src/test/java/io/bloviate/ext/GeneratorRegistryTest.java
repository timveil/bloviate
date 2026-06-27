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

package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.SimpleStringGenerator;
import io.bloviate.gen.UUIDGenerator;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class GeneratorRegistryTest {

    private static Column column(String name, JDBCType type, String typeName) {
        return new Column(name, "t", null, null, type, 32, null, typeName, false, true, null, 1);
    }

    @Test
    void resolvesByColumnNamePattern() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerColumnNamePattern("email", (c, r) -> new SimpleStringGenerator.Builder(r).size(c.maxSize()).build())
                .build();

        assertInstanceOf(SimpleStringGenerator.class, registry.resolve(column("EMAIL", JDBCType.VARCHAR, "varchar"), new Random(1)));
    }

    @Test
    void resolvesByVendorTypeName() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerTypeName("uuid", (c, r) -> new UUIDGenerator.Builder(r).build())
                .build();

        // case-insensitive match on typeName
        assertInstanceOf(UUIDGenerator.class, registry.resolve(column("id", JDBCType.OTHER, "UUID"), new Random(1)));
    }

    @Test
    void resolvesByJdbcType() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerJdbcType(JDBCType.INTEGER, (c, r) -> new IntegerGenerator.Builder(r).build())
                .build();

        assertInstanceOf(IntegerGenerator.class, registry.resolve(column("qty", JDBCType.INTEGER, "int4"), new Random(1)));
    }

    @Test
    void columnNamePatternOutranksTypeNameAndJdbcType() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerColumnNamePattern("id", (c, r) -> new SimpleStringGenerator.Builder(r).size(8).build())
                .registerTypeName("uuid", (c, r) -> new UUIDGenerator.Builder(r).build())
                .registerJdbcType(JDBCType.OTHER, (c, r) -> new IntegerGenerator.Builder(r).build())
                .build();

        // name pattern wins even though typeName and jdbcType rules also match
        assertInstanceOf(SimpleStringGenerator.class, registry.resolve(column("id", JDBCType.OTHER, "uuid"), new Random(1)));
    }

    @Test
    void typeNameOutranksJdbcType() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerTypeName("uuid", (c, r) -> new UUIDGenerator.Builder(r).build())
                .registerJdbcType(JDBCType.OTHER, (c, r) -> new IntegerGenerator.Builder(r).build())
                .build();

        assertInstanceOf(UUIDGenerator.class, registry.resolve(column("id", JDBCType.OTHER, "uuid"), new Random(1)));
    }

    @Test
    void firstRegisteredColumnNamePatternWins() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerColumnNamePattern(".*name", (c, r) -> new SimpleStringGenerator.Builder(r).size(8).build())
                .registerColumnNamePattern("first_name", (c, r) -> new UUIDGenerator.Builder(r).build())
                .build();

        // both patterns match "first_name"; the first registered one wins
        assertInstanceOf(SimpleStringGenerator.class, registry.resolve(column("first_name", JDBCType.VARCHAR, "varchar"), new Random(1)));
    }

    @Test
    void returnsNullWhenNothingMatches() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerTypeName("uuid", (c, r) -> new UUIDGenerator.Builder(r).build())
                .build();

        assertNull(registry.resolve(column("qty", JDBCType.INTEGER, "int4"), new Random(1)));
    }

    @Test
    void matchedGeneratorRemainsReproducibleUnderSeeding() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .registerJdbcType(JDBCType.INTEGER, (c, r) -> new IntegerGenerator.Builder(r).build())
                .build();

        Column column = column("qty", JDBCType.INTEGER, "int4");

        DataGenerator<?> first = registry.resolve(column, new Random(42));
        DataGenerator<?> second = registry.resolve(column, new Random(42));

        assertEquals(first.generate(), second.generate());
    }

    @Test
    void explicitlyRegisteredPluginContributesRules() {
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .register(builder -> builder.registerTypeName("uuid", (c, r) -> new UUIDGenerator.Builder(r).build()))
                .build();

        assertInstanceOf(UUIDGenerator.class, registry.resolve(column("id", JDBCType.OTHER, "uuid"), new Random(1)));
    }

    @Test
    void serviceLoaderDiscoversPlugin() {
        // TestGeneratorPlugin is declared in META-INF/services/io.bloviate.ext.GeneratorPlugin
        GeneratorRegistry registry = new GeneratorRegistry.Builder()
                .discover()
                .build();

        assertInstanceOf(UUIDGenerator.class, registry.resolve(column("anything", JDBCType.OTHER, "custom_uuid"), new Random(1)));
    }
}
