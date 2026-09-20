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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Which tables of the selected schema a fill touches: the include and exclude name patterns set with
 * {@link DatabaseFiller.Builder#includeTables(String...)} and
 * {@link DatabaseFiller.Builder#excludeTables(String...)}.
 *
 * <p>Patterns are unqualified table names, matched case-insensitively (like
 * {@link DatabaseConfiguration#tableConfiguration(String)}), with two wildcards and nothing else:
 * {@code *} matches any run of characters (including none) and {@code ?} matches exactly one. There is
 * no escape character and no other pattern syntax. With no include pattern every table is a candidate;
 * exclude patterns are applied to whatever the include patterns kept.
 *
 * <p>An include pattern that matches no table is a mistake and fails the selection, as does a
 * selection that ends up empty; an exclude pattern that matches no table only logs a warning, so an
 * exclude list can outlive a dropped table.
 *
 * @since 3.3.0
 */
final class TableSelection {

    private static final Logger logger = LoggerFactory.getLogger(TableSelection.class);

    /** The most table names listed in an error message, so a huge schema does not flood it. */
    private static final int MAX_LISTED_TABLES = 20;

    /** Selects every table. */
    static final TableSelection ALL = new TableSelection(List.of(), List.of());

    private final List<String> includes;
    private final List<String> excludes;

    /**
     * Creates a selection from already validated patterns.
     *
     * @param includes patterns of the tables to keep; empty keeps every table
     * @param excludes patterns of the tables to drop from the kept ones
     */
    TableSelection(List<String> includes, List<String> excludes) {
        this.includes = List.copyOf(includes);
        this.excludes = List.copyOf(excludes);
    }

    /**
     * Validates one name pattern.
     *
     * @param pattern the pattern to check
     * @return {@code pattern}, unchanged
     * @throws NullPointerException     if {@code pattern} is null
     * @throws IllegalArgumentException if {@code pattern} is blank
     */
    static String requirePattern(String pattern) {
        Objects.requireNonNull(pattern, "table name pattern must not be null");
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("table name pattern must not be blank");
        }
        return pattern;
    }

    /** True when any include or exclude pattern is set. */
    boolean isConfigured() {
        return !includes.isEmpty() || !excludes.isEmpty();
    }

    /**
     * Whether this selection keeps {@code tableName}, judged by name alone: it matches an include
     * pattern (when there are any) and no exclude pattern.
     *
     * @param tableName an unqualified table name
     * @return true if the table is to be filled
     */
    boolean isSelected(String tableName) {
        boolean included = includes.isEmpty() || includes.stream().anyMatch(pattern -> matches(pattern, tableName));
        return included && excludes.stream().noneMatch(pattern -> matches(pattern, tableName));
    }

    /**
     * Applies the selection to every table name found in the schema.
     *
     * @param discovered the names of all tables in the schema, in discovery order
     * @return the names to fill, in the same order
     * @throws IllegalArgumentException if an include pattern matches none of {@code discovered}, or
     *                                  the selection leaves no table to fill
     */
    List<String> select(List<String> discovered) {
        if (!isConfigured()) {
            return discovered;
        }

        List<String> unmatchedIncludes = unmatched(includes, discovered);
        if (!unmatchedIncludes.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "includeTables pattern(s) %s match no table in the selected schema (found: %s)",
                    unmatchedIncludes, listed(discovered)));
        }

        List<String> unmatchedExcludes = unmatched(excludes, discovered);
        if (!unmatchedExcludes.isEmpty()) {
            logger.warn("excludeTables pattern(s) {} match no table in the selected schema; nothing was excluded by them",
                    unmatchedExcludes);
        }

        List<String> selected = new ArrayList<>();
        for (String name : discovered) {
            if (isSelected(name)) {
                selected.add(name);
            }
        }

        if (selected.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "includeTables %s and excludeTables %s leave no table to fill (found: %s)",
                    includes, excludes, listed(discovered)));
        }

        logger.debug("table selection kept {} of {} table(s)", selected.size(), discovered.size());
        return selected;
    }

    /** {@link #select(List)} as the filter {@link io.bloviate.util.DatabaseUtils} applies to a schema's tables. */
    UnaryOperator<List<String>> asFilter() {
        return this::select;
    }

    private static List<String> unmatched(List<String> patterns, List<String> names) {
        List<String> unmatched = new ArrayList<>();
        for (String pattern : patterns) {
            if (names.stream().noneMatch(name -> matches(pattern, name))) {
                unmatched.add(pattern);
            }
        }
        return unmatched;
    }

    private static String listed(List<String> names) {
        if (names.size() <= MAX_LISTED_TABLES) {
            return names.toString();
        }
        return names.subList(0, MAX_LISTED_TABLES) + " and " + (names.size() - MAX_LISTED_TABLES) + " more";
    }

    /**
     * Case-insensitive glob match of a whole table name: {@code *} for any run of characters, {@code ?}
     * for exactly one, every other character literally.
     *
     * @param pattern the glob
     * @param name    the table name
     * @return true if {@code pattern} matches all of {@code name}
     */
    static boolean matches(String pattern, String name) {
        String p = pattern.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);

        int patternIndex = 0;
        int nameIndex = 0;
        // where the last '*' was, and how much of the name it has swallowed so far
        int starIndex = -1;
        int starMatchEnd = 0;

        while (nameIndex < n.length()) {
            if (patternIndex < p.length() && (p.charAt(patternIndex) == '?' || p.charAt(patternIndex) == n.charAt(nameIndex))) {
                patternIndex++;
                nameIndex++;
            } else if (patternIndex < p.length() && p.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starMatchEnd = nameIndex;
            } else if (starIndex >= 0) {
                // backtrack: let the last '*' swallow one more character
                patternIndex = starIndex + 1;
                nameIndex = ++starMatchEnd;
            } else {
                return false;
            }
        }

        while (patternIndex < p.length() && p.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == p.length();
    }
}
