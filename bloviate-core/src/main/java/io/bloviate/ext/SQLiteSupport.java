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

/**
 * SQLite-specific {@link DatabaseSupport}.
 *
 * <p>SQLite is an embedded, in-process database that uses dynamic typing with column
 * <em>affinity</em> (TEXT / NUMERIC / INTEGER / REAL / BLOB) derived loosely from the declared
 * type, rather than strict column types. The {@code sqlite-jdbc} driver reflects this through
 * {@link java.sql.DatabaseMetaData#getColumns} — which Bloviate reads — by collapsing the many
 * declared type names onto a small set of JDBC types:
 *
 * <ul>
 *   <li>{@code INTEGER}, {@code BIGINT}, and (by convention) {@code BOOLEAN} &rarr;
 *       {@link java.sql.JDBCType#INTEGER}</li>
 *   <li>{@code REAL}, {@code DOUBLE}, {@code NUMERIC}, {@code DECIMAL} &rarr;
 *       {@link java.sql.JDBCType#FLOAT}</li>
 *   <li>{@code TEXT}, {@code VARCHAR}, {@code BLOB}, and the textual date/time conventions
 *       ({@code DATE}, {@code DATETIME}, {@code TIMESTAMP}) &rarr;
 *       {@link java.sql.JDBCType#VARCHAR}</li>
 * </ul>
 *
 * <p>The cross-database defaults in {@link AbstractDatabaseSupport} already map all of these JDBC
 * types, so no customization is required: integers, reals, and strings are generated for the three
 * affinity classes respectively. Because SQLite has no native {@code BOOLEAN}, {@code DATE}, or
 * {@code DATETIME} types, booleans are filled as integers and dates/timestamps as text — exactly
 * the conventions SQLite itself uses — and every generated value round-trips through SQLite's
 * affinity rules.
 *
 * <p><strong>Foreign keys</strong> are off by default in SQLite (enabled per connection with
 * {@code PRAGMA foreign_keys = ON}). Bloviate derives table fill order from the foreign-key graph
 * regardless, so referentially consistent data is produced whether or not enforcement is enabled.
 *
 * @since 2.18.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class SQLiteSupport extends AbstractDatabaseSupport {

    /** Creates the SQLite support with its default configuration. */
    public SQLiteSupport() {
    }
}
