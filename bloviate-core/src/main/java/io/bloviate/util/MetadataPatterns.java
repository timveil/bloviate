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

package io.bloviate.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Turns a literal catalog, schema or table name into a pattern that matches only that name.
 *
 * <p>Several {@link DatabaseMetaData} methods take <em>LIKE patterns</em> rather than literal names:
 * {@link DatabaseMetaData#getTables} takes a {@code schemaPattern} and a {@code tableNamePattern},
 * and {@link DatabaseMetaData#getColumns} takes the same two plus a {@code columnNamePattern}. In a
 * pattern {@code _} matches any single character and {@code %} matches any sequence, so passing a
 * name containing either through unescaped widens the search: a schema named {@code tenant_1} also
 * matches {@code tenantA1}, and a table named {@code order_items} also matches {@code orderXitems}.
 * The result is tables discovered from the wrong schema, or one table's column list carrying another
 * table's columns.
 *
 * <p>The escape character is driver-specific and is read from
 * {@link DatabaseMetaData#getSearchStringEscape()}. A driver that reports no escape support (null, an
 * empty or a blank string) gets {@link #NONE}, which leaves names untouched: escaping with nothing is
 * not possible, and corrupting the name with a blank escape would be worse than the widened search.
 *
 * <p><strong>Catalogs are deliberately not escaped.</strong> The JDBC contract makes the
 * {@code catalog} argument of {@code getTables} and {@code getColumns} a literal name, not a pattern
 * ("must match the catalog name as it is stored in the database"), so escaping it would make a
 * catalog whose name contains {@code _} &mdash; a common MySQL database name &mdash; match nothing.
 *
 * @author Tim Veil
 * @see DatabaseMetaData#getSearchStringEscape()
 * @since 3.8.1
 */
public final class MetadataPatterns {

    private static final Logger logger = LoggerFactory.getLogger(MetadataPatterns.class);

    /** Escapes nothing: for drivers that report no escape character. */
    public static final MetadataPatterns NONE = new MetadataPatterns(null);

    private final String escape;

    private MetadataPatterns(String escape) {
        this.escape = escape;
    }

    /**
     * Reads the driver's escape character and returns the escaper to use with it.
     *
     * <p>A driver that cannot answer at all (an escape of null, empty or blank, or an
     * {@link SQLException} from the call itself &mdash; some drivers raise
     * {@link java.sql.SQLFeatureNotSupportedException} here) yields {@link #NONE} rather than
     * failing the metadata read: the caller then behaves exactly as it did before escaping existed.
     *
     * @param metaData the metadata to read the escape character from
     * @return an escaper for that driver, never null
     */
    public static MetadataPatterns forMetaData(DatabaseMetaData metaData) {
        String escape;

        try {
            escape = metaData.getSearchStringEscape();
        } catch (SQLException e) {
            logger.debug("driver could not report a metadata search-string escape; names will not be escaped", e);
            return NONE;
        }

        if (escape == null || escape.isBlank()) {
            logger.debug("driver reports no metadata search-string escape [{}]; names will not be escaped", escape);
            return NONE;
        }

        return new MetadataPatterns(escape);
    }

    /**
     * Returns a pattern matching exactly the given name, escaping the wildcards {@code _} and
     * {@code %} and the escape character itself.
     *
     * @param name the literal name; null is returned unchanged, since null means "do not narrow the
     *             search" to every metadata call that takes a pattern
     * @return the name as a pattern that matches only itself
     */
    public String literal(String name) {
        if (name == null || escape == null) {
            return name;
        }

        StringBuilder pattern = new StringBuilder(name.length() + name.length() / 4 + 1);

        int i = 0;
        while (i < name.length()) {
            if (name.startsWith(escape, i)) {
                pattern.append(escape).append(escape);
                i += escape.length();
                continue;
            }

            char c = name.charAt(i);
            if (c == '_' || c == '%') {
                pattern.append(escape);
            }
            pattern.append(c);
            i++;
        }

        return pattern.toString();
    }
}
