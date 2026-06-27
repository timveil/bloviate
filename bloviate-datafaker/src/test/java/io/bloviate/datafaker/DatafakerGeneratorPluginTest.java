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

package io.bloviate.datafaker;

import io.bloviate.db.Column;
import io.bloviate.ext.GeneratorRegistry;
import io.bloviate.gen.DataGenerator;
import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatafakerGeneratorPluginTest {

    private static final long SEED = 42L;

    private static final GeneratorRegistry EXPLICIT =
            new GeneratorRegistry.Builder().register(new DatafakerGeneratorPlugin()).build();

    private static Column column(String name, JDBCType type, int maxSize) {
        return new Column(name, "t", null, null, type, maxSize, null, type.getName().toLowerCase(), false, true, null, 1);
    }

    private static String value(GeneratorRegistry registry, String columnName, int maxSize) {
        DataGenerator<?> generator = registry.resolve(column(columnName, JDBCType.VARCHAR, maxSize), RandomGenerators.create(SEED));
        return (String) generator.generate();
    }

    @Test
    void emailColumnGetsReservedEmail() {
        String email = value(EXPLICIT, "email", 255);
        assertTrue(email.matches(".+@example\\.(com|net|org)"), "expected a reserved example.* email but got " + email);
    }

    @Test
    void emailMatchesEvenWhenNameContainsIt() {
        // ".*email.*" full-matches "user_email_address", and email is registered before address rules
        String email = value(EXPLICIT, "user_email_address", 255);
        assertTrue(email.matches(".+@example\\.(com|net|org)"), "expected an email but got " + email);
    }

    @Test
    void phoneColumnGetsReserved555Number() {
        String phone = value(EXPLICIT, "phone_number", 32);
        assertTrue(phone.matches("\\([0-9]{3}\\) 555-01[0-9]{2}"), "expected a reserved 555-01xx phone but got " + phone);
    }

    @Test
    void nameColumnsGetRealisticValues() {
        assertFalse(value(EXPLICIT, "first_name", 64).isBlank());
        assertFalse(value(EXPLICIT, "last_name", 64).isBlank());
        assertFalse(value(EXPLICIT, "city", 64).isBlank());
    }

    @Test
    void sameSeedProducesIdenticalValues() {
        // reproducibility: a fresh generator on the same seed yields the same realistic value
        assertEquals(value(EXPLICIT, "email", 255), value(EXPLICIT, "email", 255));
        assertEquals(value(EXPLICIT, "first_name", 64), value(EXPLICIT, "first_name", 64));
    }

    @Test
    void valueIsTruncatedToColumnSize() {
        String email = value(EXPLICIT, "email", 5);
        assertTrue(email.length() <= 5, "value must fit the column size but was " + email);
    }

    @Test
    void unmatchedColumnFallsThroughToNull() {
        // a column the dictionary doesn't recognize returns null so the engine uses its type default
        assertNull(EXPLICIT.resolve(column("widget_count", JDBCType.INTEGER, 0), RandomGenerators.create(SEED)));
    }

    @Test
    void pluginIsDiscoveredViaServiceLoader() {
        // the META-INF/services entry makes discover() pick the plugin up with no explicit registration
        GeneratorRegistry discovered = new GeneratorRegistry.Builder().discover().build();
        DataGenerator<?> generator = discovered.resolve(column("email", JDBCType.VARCHAR, 255), RandomGenerators.create(SEED));
        assertInstanceOf(DatafakerStringGenerator.class, generator);
    }
}
