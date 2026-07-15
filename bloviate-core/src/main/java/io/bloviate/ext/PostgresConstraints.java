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

import io.bloviate.db.ColumnConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads PostgreSQL value constraints — {@code ENUM} labels and the machine-readable forms of
 * {@code CHECK} constraints — so the fill engine can generate conforming values (issue #479).
 *
 * <p>Two catalog queries run per table: {@code pg_enum} for enum columns, and {@code pg_constraint}
 * (with {@code pg_get_constraintdef}) for single-column checks, whose definitions are then
 * {@link #parseCheck(String) parsed}. Anything the parser can't safely interpret is logged and
 * skipped (the engine falls back to its type default). The keys of the returned map are lower-cased
 * column names.
 *
 * @since 2.14.0
 */
final class PostgresConstraints {

    private static final Logger logger = LoggerFactory.getLogger(PostgresConstraints.class);

    // a number following a comparison operator, ignoring wrapping parens/casts: ">= (0)::numeric", "<= 5"
    private static final Pattern GREATER = Pattern.compile(">=?\\s*\\(?\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LESS = Pattern.compile("<=?\\s*\\(?\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'((?:[^']|'')*)'");

    private PostgresConstraints() {
    }

    static Map<String, ColumnConstraint> read(Connection connection, String schema, String table) {
        String namespace = (schema == null || schema.isBlank()) ? "public" : schema;
        Map<String, ColumnConstraint> constraints = new LinkedHashMap<>();
        try {
            readEnums(connection, namespace, table, constraints);
            readChecks(connection, namespace, table, constraints);
        } catch (SQLException e) {
            // constraint awareness is best-effort: never fail a fill because the catalog couldn't be read
            logger.warn("could not read constraints for table [{}]: {}", table, e.getMessage());
            // full stack (with SQLState) at debug for diagnosis without noising up the default level
            logger.debug("constraint read failure for table [{}]", table, e);
        }
        return constraints;
    }

    private static void readEnums(Connection connection, String namespace, String table, Map<String, ColumnConstraint> out) throws SQLException {
        String sql = """
                SELECT a.attname AS column_name, e.enumlabel AS label
                FROM pg_catalog.pg_attribute a
                JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
                JOIN pg_catalog.pg_enum e ON e.enumtypid = t.oid
                WHERE c.relname = ? AND n.nspname = ? AND a.attnum > 0 AND NOT a.attisdropped
                ORDER BY a.attname, e.enumsortorder""";
        Map<String, List<String>> labels = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, namespace);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    labels.computeIfAbsent(rs.getString("column_name").toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                            .add(rs.getString("label"));
                }
            }
        }
        labels.forEach((column, values) -> out.put(column, ColumnConstraint.ofValues(values)));
    }

    private static void readChecks(Connection connection, String namespace, String table, Map<String, ColumnConstraint> out) throws SQLException {
        String sql = """
                SELECT a.attname AS column_name, pg_catalog.pg_get_constraintdef(con.oid) AS def
                FROM pg_catalog.pg_constraint con
                JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY (con.conkey)
                WHERE con.contype = 'c' AND c.relname = ? AND n.nspname = ? AND cardinality(con.conkey) = 1""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, namespace);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String column = rs.getString("column_name").toLowerCase(Locale.ROOT);
                    if (out.containsKey(column)) {
                        continue; // an enum constraint already pins this column
                    }
                    String def = rs.getString("def");
                    ColumnConstraint parsed = parseCheck(def);
                    if (parsed != null) {
                        out.put(column, parsed);
                    } else {
                        logger.warn("CHECK constraint on [{}.{}] not honored (unsupported form): {}", table, column, def);
                    }
                }
            }
        }
    }

    /**
     * Parses a PostgreSQL {@code pg_get_constraintdef} string into a {@link ColumnConstraint}, or
     * returns null for forms that can't be safely honored (negations, disjunctions, patterns,
     * one-sided ranges, multi-column expressions).
     */
    static ColumnConstraint parseCheck(String def) {
        if (def == null) {
            return null;
        }
        String lower = def.toLowerCase(Locale.ROOT);
        // bail on anything we can't safely satisfy by construction
        if (lower.contains("<>") || lower.contains("!=") || lower.contains(" or ")
                || lower.contains(" not ") || lower.contains("~~") || lower.contains(" like ")) {
            return null;
        }

        // categorical: IN (...) / = ANY (ARRAY[...]) / a set of quoted literals
        if (lower.contains("array[") || lower.contains(" in (") || (def.indexOf('\'') >= 0 && !hasComparison(def))) {
            List<String> values = extractValues(def);
            return values.isEmpty() ? null : ColumnConstraint.ofValues(values);
        }

        // numeric range: pair a lower bound (>= / >) with an upper bound (<= / <)
        BigDecimal min = firstNumber(GREATER, def);
        BigDecimal max = firstNumber(LESS, def);
        boolean minInclusive = min != null && containsInclusive(def, ">=");
        boolean maxInclusive = max != null && containsInclusive(def, "<=");
        if (min != null && max != null) {
            return new ColumnConstraint(null, min, minInclusive, max, maxInclusive);
        }
        return null; // one-sided or unrecognized
    }

    private static boolean hasComparison(String def) {
        return def.contains(">") || def.contains("<");
    }

    private static boolean containsInclusive(String def, String operator) {
        return def.contains(operator);
    }

    private static BigDecimal firstNumber(Pattern pattern, String def) {
        Matcher matcher = pattern.matcher(def);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return null;
    }

    /** Extracts the literal values from an {@code IN (...)} / {@code ARRAY[...]} list or bare equality. */
    private static List<String> extractValues(String def) {
        List<String> values = new ArrayList<>();
        // prefer the contents of ARRAY[ ... ] or IN ( ... ); else fall back to every quoted literal
        String list = between(def, "array[", "]");
        if (list == null) {
            list = between(def, " in (", ")");
        }
        if (list != null) {
            for (String raw : list.split(",")) {
                String value = cleanItem(raw);
                if (value != null) {
                    values.add(value);
                }
            }
            if (!values.isEmpty()) {
                return values;
            }
        }
        // bare equality or unhandled list shape: take every single-quoted literal
        Matcher matcher = SINGLE_QUOTED.matcher(def);
        while (matcher.find()) {
            values.add(matcher.group(1).replace("''", "'"));
        }
        return values;
    }

    /** Strips a trailing {@code ::type} cast and surrounding quotes from one list item. */
    private static String cleanItem(String raw) {
        String item = raw.strip();
        int cast = item.indexOf("::");
        if (cast >= 0) {
            item = item.substring(0, cast).strip();
        }
        if (item.length() >= 2 && item.startsWith("'") && item.endsWith("'")) {
            item = item.substring(1, item.length() - 1).replace("''", "'");
        }
        return item.isEmpty() ? null : item;
    }

    private static String between(String text, String open, String close) {
        int start = text.toLowerCase(Locale.ROOT).indexOf(open);
        if (start < 0) {
            return null;
        }
        int from = start + open.length();
        int end = text.indexOf(close, from);
        return end < 0 ? null : text.substring(from, end);
    }
}
