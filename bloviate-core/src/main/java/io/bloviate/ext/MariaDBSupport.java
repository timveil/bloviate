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
 * MariaDB-specific {@link DatabaseSupport}.
 *
 * <p>MariaDB is a MySQL fork that speaks the MySQL wire protocol, so its columns surface
 * through JDBC essentially as MySQL's do. This class therefore extends {@link MySQLSupport}
 * and inherits its full type handling and its session-variable bulk-load mechanism
 * ({@code FOREIGN_KEY_CHECKS}/{@code UNIQUE_CHECKS}). The cross-database defaults cover the
 * standard types; {@code TINYINT UNSIGNED} surfaces as JDBC {@code TINYINT} (type name
 * {@code "TINYINT UNSIGNED"}) and accepts the inherited 0&ndash;255 generator.
 *
 * <p><strong>JSON cannot be auto-detected.</strong> Unlike MySQL — whose driver reports a
 * {@code JSON} column with type name {@code "JSON"}, letting {@link MySQLSupport} route it to
 * a JSON generator — MariaDB implements {@code JSON} as an alias for {@code LONGTEXT}, and the
 * MariaDB Connector/J driver reports such columns through
 * {@link java.sql.DatabaseMetaData#getColumns} as {@link java.sql.JDBCType#LONGVARCHAR} with
 * type name {@code "LONGTEXT"} — indistinguishable from an ordinary {@code LONGTEXT}/{@code TEXT}
 * column. Because MariaDB also adds an automatic {@code CHECK (json_valid(col))} constraint, a
 * random string would fail to insert. Bloviate therefore cannot generate valid JSON for a
 * MariaDB {@code JSON} column automatically; supply a per-column override (a
 * {@link io.bloviate.gen.JsonbGenerator} via
 * {@link io.bloviate.db.ColumnConfiguration}) for any such column.
 *
 * <p><strong>Product-name caveat.</strong> The MariaDB Connector/J driver reports
 * {@code "MariaDB"}, which resolves to this class. The legacy MySQL Connector/J driver
 * reports {@code "MySQL"} even against a MariaDB server and resolves to {@link MySQLSupport};
 * because this class adds no divergent behavior, that is equivalent.
 *
 * @since 2.18.0
 * @see MySQLSupport
 * @see DatabaseSupport
 */
public class MariaDBSupport extends MySQLSupport {

    /** Creates the MariaDB support with its default (MySQL-inherited) configuration. */
    public MariaDBSupport() {
    }
}
