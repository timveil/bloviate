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

import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.IndexedDataGenerator;
import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies issue #473 referential realism: columns projecting the same {@link RowContext} produce a
 * mutually consistent, reproducible, order-independent tuple per row.
 */
class ReferentialRealismTest {

    private static final long SEED = 42L;

    private static DataGenerator<?> projection(RowContext<Person> context, java.util.function.Function<Person, String> selector) {
        return context.project(selector).create(RandomGenerators.create(0));
    }

    @Test
    void derivedFieldsAgreeWithTheName() {
        Person person = People.context(SEED, Locale.ENGLISH).at(7);
        assertEquals(person.firstName() + " " + person.lastName(), person.fullName(), "full name must match its parts");
        assertEquals(person.username() + "@example.com", person.email(), "email must be the username at the reserved domain");
        String expectedHandle = person.firstName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        assertTrue(person.email().toLowerCase(Locale.ROOT).contains(expectedHandle), "email should be derived from the first name: " + person.email());
    }

    @Test
    void columnsProjectingTheSameContextStayConsistentRowByRow() {
        RowContext<Person> person = People.context(SEED, Locale.ENGLISH);
        DataGenerator<?> firstName = projection(person, Person::firstName);
        DataGenerator<?> lastName = projection(person, Person::lastName);
        DataGenerator<?> fullName = projection(person, Person::fullName);
        DataGenerator<?> email = projection(person, Person::email);
        DataGenerator<?> username = projection(person, Person::username);

        for (int row = 0; row < 100; row++) {
            // generate one cell per column, in column order, exactly as the fill loop does
            String f = (String) firstName.generate();
            String l = (String) lastName.generate();
            String full = (String) fullName.generate();
            String e = (String) email.generate();
            String u = (String) username.generate();

            assertEquals(f + " " + l, full, "row " + row + ": full name inconsistent");
            assertEquals(u + "@example.com", e, "row " + row + ": email inconsistent with username");
        }
    }

    @Test
    void sameSeedIsReproducible() {
        RowContext<Person> a = People.context(SEED, Locale.ENGLISH);
        RowContext<Person> b = People.context(SEED, Locale.ENGLISH);
        for (long row = 0; row < 1_000; row++) {
            assertEquals(a.at(row), b.at(row), "same seed must reproduce the same identity at row " + row);
        }
    }

    @Test
    void differentSeedsDiffer() {
        Person a = People.context(1L, Locale.ENGLISH).at(0);
        Person b = People.context(2L, Locale.ENGLISH).at(0);
        assertFalse(a.equals(b), "different seeds should generally yield different identities");
    }

    @Test
    void emailsAreUniqueAcrossRows() {
        RowContext<Person> person = People.context(SEED, Locale.ENGLISH);
        java.util.Set<String> emails = new java.util.HashSet<>();
        for (long row = 0; row < 5_000; row++) {
            assertTrue(emails.add(person.at(row).email()), "email collided at row " + row);
        }
    }

    @Test
    void seekReproducesSequentialOutputForPartitioning() {
        RowContext<Person> person = People.context(SEED, Locale.ENGLISH);

        DataGenerator<?> sequential = projection(person, Person::email);
        String[] expected = new String[50];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (String) sequential.generate();
        }

        for (int start : new int[]{0, 1, 17, 49}) {
            DataGenerator<?> seeker = projection(person, Person::email);
            ((IndexedDataGenerator) seeker).seek(start);
            for (int i = start; i < expected.length; i++) {
                assertEquals(expected[i], seeker.generate(), "seek(" + start + ") must match sequential email at row " + i);
            }
        }
    }
}
