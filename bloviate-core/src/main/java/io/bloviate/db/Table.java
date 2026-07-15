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
        StringJoiner nameJoiner = new StringJoiner(",");
        StringJoiner valueJoiner = new StringJoiner(",");

        for (Column column : filteredColumns()) {
            nameJoiner.add(quote(column.name(), identifierQuote));
            valueJoiner.add("?");
        }

        return String.format("insert into %s (%s) values (%s)", qualifiedName(identifierQuote), nameJoiner, valueJoiner);

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
