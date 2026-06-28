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

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

/**
 * A user-facing registry of custom {@link GeneratorFactory} rules that lets callers override
 * data generation <em>without</em> subclassing {@link DatabaseSupport}.
 *
 * <p>A registry is attached to a
 * {@link io.bloviate.db.DatabaseConfiguration DatabaseConfiguration} and consulted by the fill
 * engine for every column. It supports three kinds of matchers, resolved in a fixed, documented
 * order of precedence:
 *
 * <ol>
 *   <li><strong>column-name pattern</strong> &mdash; a case-insensitive regular expression matched
 *       against {@link Column#name()}; rules are tried in registration order and the first to match
 *       wins</li>
 *   <li><strong>vendor type name</strong> &mdash; an exact, case-insensitive match against
 *       {@link Column#typeName()} (e.g. {@code "uuid"}, {@code "jsonb"})</li>
 *   <li><strong>{@link JDBCType}</strong> &mdash; a match against {@link Column#jdbcType()}</li>
 * </ol>
 *
 * <p>The registry sits between the highest-precedence per-column
 * {@link io.bloviate.db.ColumnConfiguration ColumnConfiguration} override and the built-in
 * {@link DatabaseSupport} defaults. The full engine precedence is therefore:
 *
 * <pre>
 *   per-column ColumnConfiguration
 *     &gt; registry column-name pattern
 *     &gt; registry vendor typeName
 *     &gt; registry JDBCType
 *     &gt; built-in DatabaseSupport default
 * </pre>
 *
 * <p>If no rule matches, {@link #resolve(Column, RandomGenerator)} returns {@code null} and the engine falls
 * back to the {@link DatabaseSupport} default. Matched generators are constructed with the
 * engine-supplied, seeded {@link RandomGenerator}, so they remain reproducible like every built-in generator.
 *
 * <p>Instances are immutable and built through the {@link Builder}. External jars can contribute
 * rules automatically by providing a {@link GeneratorPlugin} discovered via
 * {@link Builder#discover()}.
 *
 * @see GeneratorPlugin
 * @see GeneratorFactory
 * @see io.bloviate.db.DatabaseConfiguration
 */
public final class GeneratorRegistry {

    private record NameRule(Pattern pattern, GeneratorFactory factory) {
    }

    private final List<NameRule> nameRules;
    private final Map<String, GeneratorFactory> typeNameRules;
    private final Map<JDBCType, GeneratorFactory> jdbcTypeRules;

    private GeneratorRegistry(Builder builder) {
        this.nameRules = List.copyOf(builder.nameRules);
        this.typeNameRules = Map.copyOf(builder.typeNameRules);
        this.jdbcTypeRules = builder.jdbcTypeRules.isEmpty()
                ? Map.of()
                : new EnumMap<>(builder.jdbcTypeRules);
    }

    /**
     * Resolves the custom generator for a column, honouring the documented precedence
     * (name pattern &gt; vendor typeName &gt; JDBCType).
     *
     * @param column the column metadata to match against
     * @param random the engine-seeded random source to construct the generator with
     * @return the matching generator, or {@code null} if no rule applies (caller should fall back to
     * the {@link DatabaseSupport} default)
     */
    public DataGenerator<?> resolve(Column column, RandomGenerator random) {
        String name = column.name();
        if (name != null) {
            for (NameRule rule : nameRules) {
                if (rule.pattern().matcher(name).matches()) {
                    return rule.factory().create(column, random);
                }
            }
        }

        String typeName = column.typeName();
        if (typeName != null) {
            GeneratorFactory byTypeName = typeNameRules.get(typeName.toLowerCase(Locale.ROOT));
            if (byTypeName != null) {
                return byTypeName.create(column, random);
            }
        }

        GeneratorFactory byJdbcType = jdbcTypeRules.get(column.jdbcType());
        if (byJdbcType != null) {
            return byJdbcType.create(column, random);
        }

        return null;
    }

    /**
     * Builds an immutable {@link GeneratorRegistry}. Registration methods may be called in any
     * order and any number of times; {@code register*} methods return {@code this} for chaining.
     *
     * <p>Within a single matcher kind, later registrations win for vendor type names and
     * {@link JDBCType}s (last-write replaces), while column-name patterns preserve registration
     * order (first match wins at resolution time).
     */
    public static final class Builder {

        private final List<NameRule> nameRules = new ArrayList<>();
        private final Map<String, GeneratorFactory> typeNameRules = new LinkedHashMap<>();
        private final Map<JDBCType, GeneratorFactory> jdbcTypeRules = new EnumMap<>(JDBCType.class);

        /** Creates an empty builder with no rules registered. */
        public Builder() {
        }

        /**
         * Registers a generator for columns whose {@link Column#name() name} matches the given
         * regular expression. Matching is full ({@link java.util.regex.Matcher#matches()}) and
         * case-insensitive &mdash; use {@code "email"} or {@code ".*_email"} rather than relying on
         * substring search. Patterns are evaluated in registration order; the first match wins.
         *
         * @param regex   the column-name pattern (full-match, case-insensitive)
         * @param factory the generator factory to use on a match
         * @return this builder
         */
        public Builder registerColumnNamePattern(String regex, GeneratorFactory factory) {
            nameRules.add(new NameRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), factory));
            return this;
        }

        /**
         * Registers a generator for columns whose database-specific {@link Column#typeName() type
         * name} equals the given value, ignoring case (e.g. {@code "uuid"}, {@code "jsonb"}).
         *
         * @param typeName the vendor type name to match
         * @param factory  the generator factory to use on a match
         * @return this builder
         */
        public Builder registerTypeName(String typeName, GeneratorFactory factory) {
            typeNameRules.put(typeName.toLowerCase(Locale.ROOT), factory);
            return this;
        }

        /**
         * Registers a generator override for a {@link JDBCType}. This takes precedence over the
         * built-in {@link DatabaseSupport} default for that type.
         *
         * @param jdbcType the JDBC type to match
         * @param factory  the generator factory to use on a match
         * @return this builder
         */
        public Builder registerJdbcType(JDBCType jdbcType, GeneratorFactory factory) {
            jdbcTypeRules.put(jdbcType, factory);
            return this;
        }

        /**
         * Applies a {@link GeneratorPlugin} immediately, letting it register rules at this point in
         * the builder chain.
         *
         * @param plugin the plugin to apply
         * @return this builder
         */
        public Builder register(GeneratorPlugin plugin) {
            plugin.contribute(this);
            return this;
        }

        /**
         * Discovers and applies every {@link GeneratorPlugin} visible to the current thread's
         * context class loader via {@link ServiceLoader}. Discovery order is not specified by
         * {@code ServiceLoader}; call this where you want discovered rules to apply relative to your
         * explicit registrations.
         *
         * @return this builder
         */
        public Builder discover() {
            for (GeneratorPlugin plugin : ServiceLoader.load(GeneratorPlugin.class)) {
                plugin.contribute(this);
            }
            return this;
        }

        /**
         * Builds the registry from the rules registered so far.
         *
         * @return an immutable registry holding the rules registered so far
         */
        public GeneratorRegistry build() {
            return new GeneratorRegistry(this);
        }
    }
}
