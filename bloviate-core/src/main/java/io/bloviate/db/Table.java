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

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Represents a database table with its columns, keys, and relationships.
 *
 * <p>This immutable record encapsulates all metadata for a single database table,
 * including its columns, primary key, and foreign key relationships. It provides
 * utility methods for generating SQL statements and filtering columns based on
 * their characteristics.
 *
 * <p>Tables are typically discovered through database metadata analysis and are
 * used by {@link TableFiller} to generate appropriate data for each column.
 *
 * @param name the table name
 * @param primaryKey the primary key definition, may be null if no primary key exists
 * @param columns the list of all columns in this table
 * @param foreignKeys the list of foreign key relationships from this table to others
 *
 * @author Tim Veil
 * @see Column
 * @see PrimaryKey
 * @see ForeignKey
 * @see TableFiller
 */
public record Table(String name, PrimaryKey primaryKey, List<Column> columns, List<ForeignKey> foreignKeys) {

    /**
     * Copies the column and foreign-key lists so the record is deeply immutable — table
     * metadata is shared across worker threads during parallel fills.
     */
    public Table {
        columns = columns == null ? null : List.copyOf(columns);
        foreignKeys = foreignKeys == null ? null : List.copyOf(foreignKeys);
    }

    /**
     * Generates an SQL INSERT statement template for this table with unquoted, unqualified
     * identifiers.
     *
     * <p>This legacy form emits the bare table name (no schema qualification) and raw column
     * names, exactly as earlier releases did. Prefer {@link #insertString(String)} when a
     * {@link java.sql.DatabaseMetaData#getIdentifierQuoteString() quote string} is available;
     * unquoted identifiers break on reserved words, mixed-case names, and names containing
     * special characters.
     *
     * @return a parameterized SQL INSERT statement string
     */
    public String insertString() {
        StringJoiner nameJoiner = new StringJoiner(",");
        StringJoiner valueJoiner = new StringJoiner(",");

        for (Column column : filteredColumns()) {
            nameJoiner.add(column.name());
            valueJoiner.add("?");
        }

        return String.format("insert into %s (%s) values (%s)", name, nameJoiner, valueJoiner);
    }

    /**
     * Generates an SQL INSERT statement template for this table.
     *
     * <p>Creates a parameterized INSERT statement using question mark placeholders
     * for all non-auto-increment columns. Auto-increment columns are excluded
     * as they are populated automatically by the database.
     *
     * <p>The table and column names come from database metadata and are emitted quoted with
     * the supplied identifier quote string (embedded quote characters doubled), so reserved
     * words, mixed-case, and otherwise exotic identifiers round-trip exactly as the catalog
     * reported them and cannot alter the statement's structure. The table name is qualified
     * with the schema its columns were introspected from, so the fill targets the introspected
     * table even when the connection's current schema or search path differs.
     *
     * @param identifierQuote the identifier quote string reported by
     *                        {@link java.sql.DatabaseMetaData#getIdentifierQuoteString()};
     *                        {@code null} or blank (JDBC reports a single space when quoting
     *                        is unsupported) emits unquoted identifiers
     * @return a parameterized SQL INSERT statement string
     */
    public String insertString(String identifierQuote) {
        return insertString(identifierQuote, null);
    }

    /**
     * Generates an SQL INSERT statement template whose values may be SQL expressions rather than
     * bare placeholders.
     *
     * <p>Identical to {@link #insertString(String)} except that each value slot is taken from
     * {@code valueExpressions} instead of being a {@code ?}. This exists for columns whose type the
     * driver cannot bind and which must therefore be constructed in SQL &mdash; BigQuery's
     * {@code PARSE_JSON(?)} and {@code ST_GEOGFROMTEXT(?)}, for example. Expressions come from
     * {@link io.bloviate.gen.DataGenerator#valueExpression()}, so the generator resolved for a
     * column decides its own binding.
     *
     * <p>Each expression must contain <strong>exactly one</strong> {@code ?}. The engine binds one
     * parameter per column by position, so an expression with none or several would shift every
     * later parameter onto the wrong column &mdash; a silent data-corruption failure rather than an
     * error &mdash; and is rejected here instead.
     *
     * @param identifierQuote the identifier quote string, as in {@link #insertString(String)}
     * @param valueExpressions one expression per {@link #filteredColumns() filtered column}, in
     *                         order; {@code null} means a bare {@code ?} for every column
     * @return a parameterized SQL INSERT statement string
     * @throws IllegalArgumentException if the list size does not match the filtered column count
     * @throws IllegalStateException    if any expression does not contain exactly one {@code ?}
     * @since 3.2.0
     */
    public String insertString(String identifierQuote, List<String> valueExpressions) {
        List<Column> columns = filteredColumns();

        if (valueExpressions != null && valueExpressions.size() != columns.size()) {
            throw new IllegalArgumentException(String.format(
                    "table [%s] has %d fillable columns but %d value expressions were supplied",
                    name, columns.size(), valueExpressions.size()));
        }

        StringJoiner nameJoiner = new StringJoiner(",");
        StringJoiner valueJoiner = new StringJoiner(",");

        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            nameJoiner.add(quote(column.name(), identifierQuote));
            valueJoiner.add(valueExpressions == null
                    ? "?"
                    : validatedExpression(valueExpressions.get(i), column));
        }

        return String.format("insert into %s (%s) values (%s)", qualifiedName(identifierQuote), nameJoiner, valueJoiner);
    }

    /**
     * Checks that a value expression binds exactly one parameter, so column {@code i}'s generator
     * always writes to parameter {@code i + 1}.
     */
    private static String validatedExpression(String expression, Column column) {
        if (expression == null) {
            throw new IllegalStateException(String.format(
                    "value expression for column [%s.%s] is null; it must contain exactly one '?'",
                    column.tableName(), column.name()));
        }

        int placeholders = 0;
        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) == '?') {
                placeholders++;
            }
        }

        if (placeholders != 1) {
            throw new IllegalStateException(String.format(
                    "value expression [%s] for column [%s.%s] contains %d '?' placeholders; it must contain "
                            + "exactly one, because the engine binds one parameter per column by position",
                    expression, column.tableName(), column.name(), placeholders));
        }

        return expression;
    }

    private String qualifiedName(String identifierQuote) {
        String schema = columns.isEmpty() ? null : columns.getFirst().schema();
        String quotedName = quote(name, identifierQuote);
        return schema == null ? quotedName : quote(schema, identifierQuote) + "." + quotedName;
    }

    private static String quote(String identifier, String identifierQuote) {
        if (identifierQuote == null || identifierQuote.isBlank()) {
            return identifier;
        }
        return identifierQuote + identifier.replace(identifierQuote, identifierQuote + identifierQuote) + identifierQuote;
    }

    /**
     * Returns a list of columns excluding auto-increment columns.
     *
     * <p>This filtered list is used for INSERT operations since auto-increment
     * columns should not have values explicitly provided.
     *
     * @return a new list containing only non-auto-increment columns
     */
    public List<Column> filteredColumns() {
        List<Column> filtered = new ArrayList<>();

        for (Column column : columns) {
            // autoIncrement() is a nullable Boolean (JDBC IS_AUTOINCREMENT may be reported
            // as ""/unknown); treat anything other than an explicit TRUE as not auto-increment
            if (!Boolean.TRUE.equals(column.autoIncrement())) {
                filtered.add(column);
            }
        }

        return filtered;
    }

    /**
     * Finds a column by name using case-insensitive comparison.
     *
     * @param name the name of the column to find
     * @return the column with the specified name, or null if not found
     */
    public Column findColumn(String name) {
        for (Column column : columns) {
            if (column.name().equalsIgnoreCase(name)) {
                return column;
            }
        }

        return null;
    }

}
