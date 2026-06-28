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

import io.bloviate.ext.GeneratorPlugin;
import io.bloviate.ext.GeneratorRegistry;
import net.datafaker.Faker;

import java.util.Locale;
import java.util.function.Function;

/**
 * A {@link GeneratorPlugin} that maps common column <em>names</em> to realistic
 * <a href="https://www.datafaker.net/">Datafaker</a> providers, so an {@code email VARCHAR} gets a
 * plausible email instead of random text. This is the opt-in semantic-data layer (issue #472).
 *
 * <p><strong>Opt-in, twice over.</strong> Nothing changes unless you (a) put {@code bloviate-datafaker}
 * on the classpath and (b) build a {@link GeneratorRegistry} that includes this plugin — either via
 * {@link GeneratorRegistry.Builder#discover()} (it is registered as a {@link java.util.ServiceLoader}
 * provider) or explicitly with {@link GeneratorRegistry.Builder#register(GeneratorPlugin)}. The
 * registry is consulted between per-column overrides and the built-in {@code DatabaseSupport}
 * defaults, which remain the fallback for every unmatched column.
 *
 * <p><strong>Safety defaults.</strong> Emails use Datafaker's reserved {@code example.*} domains and
 * phone numbers the reserved {@code 555-01xx} range, so generated identifiers never collide with real
 * ones. Values are truncated to the column's length. The locale is fixed (default {@link Locale#ENGLISH})
 * for reproducible, charset-safe output; pass another locale to the {@link #DatafakerGeneratorPlugin(Locale)}
 * constructor and register it explicitly if you need one.
 *
 * <p><strong>Caveats.</strong> Realistic values are drawn from bounded dictionaries, so they can repeat
 * at high row counts — do not route {@code UNIQUE}/primary-key columns through this plugin (keep
 * Bloviate's sequence/seeded generators for those). Realistic generation is also slower than the
 * type-driven default, so it is best kept off the highest-throughput fills.
 *
 * @since 2.11.0
 */
public class DatafakerGeneratorPlugin implements GeneratorPlugin {

    private final Locale locale;

    /** Uses {@link Locale#ENGLISH}; this is the constructor the {@link java.util.ServiceLoader} calls. */
    public DatafakerGeneratorPlugin() {
        this(Locale.ENGLISH);
    }

    /**
     * Creates a plugin that generates realistic values in the given locale.
     *
     * @param locale the locale for realistic values; fixed (not the JVM default) for reproducibility
     */
    public DatafakerGeneratorPlugin(Locale locale) {
        this.locale = locale;
    }

    @Override
    public void contribute(GeneratorRegistry.Builder builder) {
        // Patterns are full-match and case-insensitive; the first match wins, so order matters —
        // e.g. ".*email.*" is registered before ".*address.*" so "email_address" stays an email.
        rule(builder, ".*email.*", f -> f.internet().safeEmailAddress());
        rule(builder, ".*first_?name.*", f -> f.name().firstName());
        rule(builder, ".*last_?name.*", f -> f.name().lastName());
        rule(builder, ".*user_?name.*", f -> f.name().username());
        rule(builder, ".*full_?name.*|name", f -> f.name().fullName());
        rule(builder, ".*phone.*", f -> f.regexify("\\([0-9]{3}\\) 555-01[0-9]{2}"));
        rule(builder, ".*company.*", f -> f.company().name());
        rule(builder, ".*(url|website).*", f -> f.internet().url());
        rule(builder, ".*city.*", f -> f.address().city());
        rule(builder, ".*(state|province).*", f -> f.address().state());
        rule(builder, ".*country.*", f -> f.address().country());
        rule(builder, ".*(zip|postal).*", f -> f.address().zipCode());
        rule(builder, ".*(street|address).*", f -> f.address().streetAddress());
    }

    private void rule(GeneratorRegistry.Builder builder, String regex, Function<Faker, String> valueFunction) {
        builder.registerColumnNamePattern(regex, (column, random) ->
                new DatafakerStringGenerator(random, locale, column.maxSize(), valueFunction));
    }
}
