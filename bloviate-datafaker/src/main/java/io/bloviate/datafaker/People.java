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

import io.bloviate.util.Mixers;
import net.datafaker.Faker;
import net.datafaker.service.RandomService;

import java.util.Locale;
import java.util.Random;

/**
 * Builds a {@link RowContext} of coherent {@link Person} identities for correlated person columns
 * (issue #473). For each row a name is drawn from Datafaker, and the email, username, and full name
 * are <em>derived from that name</em>, so the columns agree.
 *
 * <pre>{@code
 * RowContext<Person> person = People.context(seed, Locale.ENGLISH);
 * Set.of(
 *     new ColumnConfiguration("first_name", person.project(Person::firstName)),
 *     new ColumnConfiguration("last_name",  person.project(Person::lastName)),
 *     new ColumnConfiguration("full_name",  person.project(Person::fullName)),
 *     new ColumnConfiguration("email",      person.project(Person::email)),
 *     new ColumnConfiguration("username",   person.project(Person::username)));
 * }</pre>
 *
 * <p>The identity is a pure function of {@code (seed, rowIndex)} (mixed with splitmix64), so the data
 * is reproducible and order-independent under parallel/partitioned fills. Email and username carry a
 * row-index suffix ({@code jane.doe42@example.com}) so they are unique even at large row counts while
 * still reading as derived from the name; the domain is the reserved {@code example.com}.
 *
 * @since 2.12.0
 * @see RowContext
 * @see Person
 */
public final class People {

    private People() {
    }

    /**
     * A context of coherent {@link Person} identities.
     *
     * @param seed   the group seed; vary it for a different (still reproducible) set of identities
     * @param locale the locale for names (fixed for cross-machine reproducibility)
     * @return a row context wireable via {@link RowContext#project}
     */
    public static RowContext<Person> context(long seed, Locale locale) {
        return new RowContext<>(rowIndex -> build(seed, rowIndex, locale));
    }

    private static Person build(long seed, long rowIndex, Locale locale) {
        Faker faker = new Faker(locale, new RandomService(new Random(Mixers.splitmix64(seed + rowIndex))));
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String fullName = firstName + " " + lastName;
        // derive a unique handle from the name so email/username agree with first/last and don't
        // collide at scale
        String handle = sanitize(firstName) + "." + sanitize(lastName) + rowIndex;
        String email = handle + "@example.com";
        return new Person(firstName, lastName, fullName, email, handle);
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
