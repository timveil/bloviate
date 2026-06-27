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

import io.bloviate.gen.AbstractDataGenerator;
import net.datafaker.Faker;
import net.datafaker.service.RandomService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Produces a realistic {@link String} value from a <a href="https://www.datafaker.net/">Datafaker</a>
 * provider — an email, a name, a phone number, an address part, and so on. The provider is supplied as
 * a {@code Function<Faker, String>} by {@link DatafakerGeneratorPlugin}.
 *
 * <p><strong>Reproducibility.</strong> Datafaker needs a {@link Random}; this generator seeds one from
 * the engine's column-seeded {@link RandomGenerator}, so the same base seed yields the same realistic
 * values on every run. {@link #reseed(long)} rebuilds the {@link Faker} so the output stays
 * deterministic under foreign-key wraparound and intra-table partitioning too. The {@link Locale} is
 * fixed (not the JVM default) so output is identical across machines.
 *
 * <p>If the produced value is longer than the column's declared size it is truncated, so locale-specific
 * output cannot overflow a {@code VARCHAR(n)}.
 *
 * @since 2.11.0
 */
public class DatafakerStringGenerator extends AbstractDataGenerator<String> {

    private final Locale locale;
    private final Integer maxSize;
    private final Function<Faker, String> valueFunction;
    private Faker faker;

    /**
     * @param random        the engine-supplied, column-seeded random source
     * @param locale        the locale for realistic values (fixed for reproducibility)
     * @param maxSize        the column's max length, or null/0 for unbounded; longer values are truncated
     * @param valueFunction the Datafaker provider to draw from
     */
    public DatafakerStringGenerator(RandomGenerator random, Locale locale, Integer maxSize, Function<Faker, String> valueFunction) {
        super(random);
        this.locale = locale;
        this.maxSize = maxSize;
        this.valueFunction = valueFunction;
        this.faker = newFaker(random);
    }

    private Faker newFaker(RandomGenerator source) {
        // derive a deterministic seed from the column's RNG so the Faker is reproducible
        return new Faker(locale, new RandomService(new Random(source.nextLong())));
    }

    @Override
    public String generate() {
        String value = valueFunction.apply(faker);
        if (value != null && maxSize != null && maxSize > 0 && value.length() > maxSize) {
            return value.substring(0, maxSize);
        }
        return value;
    }

    @Override
    public void reseed(long seed) {
        super.reseed(seed);
        this.faker = newFaker(this.random);
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }
}
