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
import io.bloviate.gen.TruncatedDateGenerator;
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
 * Reads {@code pg_catalog} value constraints — {@code ENUM} labels and the machine-readable forms of
 * {@code CHECK} constraints — so the fill engine can generate conforming values (issue #479).
 *
 * <p>The catalog queries and the parser serve <strong>CockroachDB</strong> as well as PostgreSQL
 * (issue #633): the {@code pg_constraint} and {@code pg_enum} queries run unchanged there, and the
 * definition text differs only in details the parser accepts on both — a {@code :::} cast, the comma
 * form of {@code extract}, and a {@code BETWEEN} that PostgreSQL would have expanded.
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

    private static final Pattern SINGLE_QUOTED = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern CONSTRAINT_FLAGS = Pattern.compile("(?i)(?:\\s+(?:NOT VALID|NO INHERIT))+\\s*$");
    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("\"(?:[^\"]|\"\")+\"");

    // a name followed by "(" that is neither a keyword nor a type with a length: a function call
    private static final Pattern FUNCTION_CALL = Pattern.compile(
            "(?<![\\w$])(?!(?:check|in|any|all|some|and|or|not|array|varying|varchar|char|character|bpchar|numeric|decimal"
                    + "|bit|time|timestamp|timestamptz|interval|float)\\b)[a-z_][a-z0-9_$]*\\s*\\(");

    // pg_get_constraintdef building blocks (the verbose, normalised text PostgreSQL stores)
    private static final String IDENT = "(\"(?:[^\"]|\"\")+\"|[A-Za-z_][A-Za-z0-9_$]*)";
    private static final String TYPE = "(?:character varying|double precision|(?:timestamp|time) with(?:out)? time zone"
            + "|bit varying|[A-Za-z_][A-Za-z0-9_]*)(?:\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?(?:\\[\\])?";
    // PostgreSQL writes "::", CockroachDB may write ":::"
    private static final String CAST = "(?:\\s*::+\\s*" + TYPE + ")?";
    /** A bare column, optionally parenthesised and cast: {@code status}, {@code (status)::text}. */
    private static final String COLUMN = "\\(*\\s*" + IDENT + "\\s*\\)*" + CAST;
    /** An optionally signed integer or decimal, as every one of these definitions writes one. */
    private static final String NUMBER = "-?\\d+(?:\\.\\d+)?";
    /** A quoted or numeric literal with an optional cast. */
    private static final String LITERAL = "(?:'(?:[^']|'')*'|" + NUMBER + ")" + CAST;
    private static final String LIST_ITEMS = "(?<list>(?:" + LITERAL + "\\s*,\\s*)*" + LITERAL + ")";

    private static final Pattern DATE_TRUNC = Pattern.compile(
            "(?is)\\s*CHECK\\s*\\(+\\s*date_trunc\\(\\s*'([a-z]+)'" + CAST + "\\s*,\\s*" + COLUMN + "\\s*\\)\\s*=\\s*" + COLUMN
                    + "\\s*\\)+");
    // the SQL-standard EXTRACT(day FROM col), PostgreSQL's date_part('day', col), and the comma
    // form CockroachDB stores for both of them: extract('day'::STRING, col)
    private static final Pattern EXTRACT_DAY = Pattern.compile(
            "(?is)\\s*CHECK\\s*\\(+\\s*(?:EXTRACT\\(\\s*day\\s+FROM\\s+" + COLUMN + "\\s*\\)"
                    + "|(?:extract|date_part)\\(\\s*'day'" + CAST + "\\s*,\\s*" + COLUMN + "\\s*\\))"
                    + "\\s*=\\s*\\(*\\s*1(?:\\.0+)?\\s*\\)*" + CAST + "\\s*\\)+");

    private static final Pattern ANY_ARRAY = Pattern.compile(
            "(?is)\\s*CHECK\\s*\\(+\\s*" + COLUMN + "\\s*=\\s*ANY\\s*\\(+\\s*ARRAY\\[\\s*" + LIST_ITEMS
                    + "\\s*\\]\\s*\\)*" + CAST + "\\s*\\)+");
    private static final Pattern IN_LIST = Pattern.compile(
            "(?is)\\s*CHECK\\s*\\(+\\s*" + COLUMN + "\\s+IN\\s*\\(\\s*" + LIST_ITEMS + "\\s*\\)\\s*\\)+");
    private static final Pattern EQUALS_LITERAL = Pattern.compile(
            "(?is)\\s*CHECK\\s*\\(+\\s*" + COLUMN + "\\s*=\\s*(?<list>" + LITERAL + ")\\s*\\)+");
    // one literal (quoted text in group 1, or a number in group 2), with any cast skipped
    private static final Pattern LIST_ITEM = Pattern.compile("'((?:[^']|'')*)'" + CAST + "|(" + NUMBER + ")" + CAST);

    // one comparison of the bare column against a number: "(amount >= (0)::numeric)", "rating <= 5"
    private static final Pattern COMPARISON = Pattern.compile(
            COLUMN + "\\s*(>=|<=|>|<)\\s*\\(*\\s*'?(" + NUMBER + ")'?\\s*\\)*" + CAST);
    private static final Pattern RANGE_STRUCTURE = Pattern.compile(
            "(?is)[\\s()]*CHECK[\\s()]*(?:AND[\\s()]*)*");
    // PostgreSQL expands BETWEEN into a pair of comparisons; CockroachDB stores it verbatim
    private static final Pattern BETWEEN = Pattern.compile(
            "(?is)\\s*CHECK\\s*\\(+\\s*" + COLUMN + "\\s+BETWEEN\\s+\\(*\\s*(?<min>" + NUMBER + ")\\s*\\)*" + CAST
                    + "\\s+AND\\s+\\(*\\s*(?<max>" + NUMBER + ")\\s*\\)*" + CAST + "\\s*\\)+");

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
     * returns null for forms that can't be safely honored.
     *
     * <p>Only the exact shapes below are recognised; <strong>anything else &mdash; in particular any
     * expression containing a function call &mdash; returns null</strong> so the engine falls back to
     * the column's type default with a warning (issue #619: {@code date_trunc('month', d) = d} used to
     * be read as the allowed value {@code month}):
     * <ul>
     *   <li>{@code col IN (...)}, {@code col = ANY (ARRAY[...])} and {@code col = 'literal'} against
     *       literals, where {@code col} is the bare column, optionally cast ({@code (status)::text});</li>
     *   <li>closed numeric ranges: {@code >=}/{@code >} paired with {@code <=}/{@code <} against numeric
     *       literals on the bare column (PostgreSQL stores {@code BETWEEN} in this form), and
     *       {@code col BETWEEN min AND max} itself, which CockroachDB stores verbatim;</li>
     *   <li>first-of-period dates: {@code date_trunc('month'|'quarter'|'year', col) = col} and
     *       {@code EXTRACT(day FROM col) = 1}, in either of its comma spellings
     *       ({@code date_part('day', col) = 1}, {@code extract('day', col) = 1}).</li>
     * </ul>
     * Negations, disjunctions, patterns, one-sided ranges, arithmetic and multi-column expressions are
     * rejected.
     */
    static ColumnConstraint parseCheck(String rawDef) {
        if (rawDef == null) {
            return null;
        }
        // pg_get_constraintdef may append a validity / inheritance flag; it says nothing about the values
        String def = CONSTRAINT_FLAGS.matcher(rawDef).replaceFirst("");
        // literals and quoted identifiers are opaque: what is inside them must never be read as syntax
        String scrubbed = QUOTED_IDENTIFIER.matcher(SINGLE_QUOTED.matcher(def).replaceAll("''")).replaceAll("id")
                .toLowerCase(Locale.ROOT);
        // bail on anything we can't safely satisfy by construction
        if (scrubbed.contains("<>") || scrubbed.contains("!=") || scrubbed.contains(" or ")
                || scrubbed.contains(" not ") || scrubbed.contains("~~") || scrubbed.contains(" like ")) {
            return null;
        }

        TruncatedDateGenerator.Unit unit = parseFirstOfPeriod(def);
        if (unit != null) {
            return ColumnConstraint.ofDateTruncation(unit);
        }

        // a function call anywhere means the check is about a computed value, not the column itself
        if (FUNCTION_CALL.matcher(scrubbed).find()) {
            return null;
        }

        ColumnConstraint categorical = parseCategorical(def);
        if (categorical != null) {
            return categorical;
        }

        ColumnConstraint between = parseBetween(def);
        return between != null ? between : parseRange(def);
    }

    /**
     * {@code col BETWEEN min AND max} on the bare column, which is always an inclusive closed range.
     *
     * <p>PostgreSQL expands {@code BETWEEN} into a pair of comparisons before storing the definition,
     * so only CockroachDB reaches this. {@code BETWEEN SYMMETRIC} does not match &mdash; its bounds
     * may be the wrong way round &mdash; and falls through to be rejected.
     */
    private static ColumnConstraint parseBetween(String def) {
        Matcher matcher = BETWEEN.matcher(def);
        if (!matcher.matches()) {
            return null;
        }
        BigDecimal min = new BigDecimal(matcher.group("min"));
        BigDecimal max = new BigDecimal(matcher.group("max"));
        if (min.compareTo(max) > 0) {
            return null; // an empty range: nothing satisfies it, so there is nothing to generate
        }
        return new ColumnConstraint(null, min, true, max, true);
    }

    /**
     * The unit of a first-of-period check, or null. Accepts the stored forms
     * {@code date_trunc('month'::text, (c)::timestamp with time zone) = c},
     * {@code date_trunc('month'::text, c) = c} and {@code EXTRACT(day FROM c) = (1)::numeric}.
     */
    private static TruncatedDateGenerator.Unit parseFirstOfPeriod(String def) {
        Matcher trunc = DATE_TRUNC.matcher(def);
        if (trunc.matches()) {
            // both sides must name the same column
            if (!unquote(trunc.group(2)).equals(unquote(trunc.group(3)))) {
                return null;
            }
            return switch (trunc.group(1).toLowerCase(Locale.ROOT)) {
                case "month" -> TruncatedDateGenerator.Unit.MONTH;
                case "quarter" -> TruncatedDateGenerator.Unit.QUARTER;
                case "year" -> TruncatedDateGenerator.Unit.YEAR;
                default -> null;
            };
        }
        return EXTRACT_DAY.matcher(def).matches() ? TruncatedDateGenerator.Unit.MONTH : null;
    }

    /** Normalises an identifier the way PostgreSQL compares it: quoted as written, bare folded to lower case. */
    private static String unquote(String identifier) {
        String id = identifier.strip();
        if (id.length() >= 2 && id.startsWith("\"") && id.endsWith("\"")) {
            return id.substring(1, id.length() - 1).replace("\"\"", "\"");
        }
        return id.toLowerCase(Locale.ROOT);
    }

    /** {@code col IN (...)}, {@code col = ANY (ARRAY[...])} or {@code col = 'literal'} on the bare column. */
    private static ColumnConstraint parseCategorical(String def) {
        String list = null;
        for (Pattern shape : List.of(ANY_ARRAY, IN_LIST, EQUALS_LITERAL)) {
            Matcher shapeMatcher = shape.matcher(def);
            if (shapeMatcher.matches()) {
                list = shapeMatcher.group("list");
                break;
            }
        }
        if (list == null) {
            return null;
        }
        // the shape regex has already proved every item is a literal, so just pull them out
        List<String> values = new ArrayList<>();
        Matcher item = LIST_ITEM.matcher(list);
        while (item.find()) {
            values.add(item.group(1) != null ? item.group(1).replace("''", "'") : item.group(2));
        }
        return values.isEmpty() ? null : ColumnConstraint.ofValues(values);
    }

    /** A closed range built from comparisons of one bare column against numeric literals, and nothing else. */
    private static ColumnConstraint parseRange(String def) {
        Matcher matcher = COMPARISON.matcher(def);
        BigDecimal min = null;
        BigDecimal max = null;
        boolean minInclusive = false;
        boolean maxInclusive = false;
        String column = null;
        StringBuilder remainder = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            remainder.append(def, last, matcher.start());
            last = matcher.end();
            String comparedColumn = unquote(matcher.group(1));
            if (column != null && !column.equals(comparedColumn)) {
                return null;
            }
            column = comparedColumn;
            BigDecimal number = new BigDecimal(matcher.group(3));
            String operator = matcher.group(2);
            if (operator.startsWith(">")) {
                if (min != null) {
                    return null; // two lower bounds
                }
                min = number;
                minInclusive = ">=".equals(operator);
            } else {
                if (max != null) {
                    return null; // two upper bounds
                }
                max = number;
                maxInclusive = "<=".equals(operator);
            }
        }
        remainder.append(def, last, def.length());
        // whatever surrounds the comparisons may only be structure: CHECK, parentheses, AND
        if (min == null || max == null || !RANGE_STRUCTURE.matcher(remainder).matches()) {
            return null; // one-sided or unrecognized
        }
        return new ColumnConstraint(null, min, minInclusive, max, maxInclusive);
    }
}
