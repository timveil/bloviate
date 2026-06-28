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
 * CockroachDB-specific {@link DatabaseSupport}.
 *
 * <p>CockroachDB is reached through the PostgreSQL JDBC driver and is wire-compatible with
 * PostgreSQL, so its columns surface through JDBC exactly as PostgreSQL's do (UUID, JSONB,
 * INET, INTERVAL, bit strings, and {@code _text}/{@code _int4}/{@code _int8} arrays). This
 * class therefore extends {@link PostgresSupport} and inherits its full type handling; it
 * exists as a distinct type for explicit selection and product-name resolution.
 *
 * @since 1.0.0
 * @see PostgresSupport
 * @see DatabaseSupport
 */
public class CockroachDBSupport extends PostgresSupport {

    /** Creates the CockroachDB support with its default configuration. */
    public CockroachDBSupport() {
    }

    /**
     * CockroachDB ignores the PostgreSQL driver's {@code reWriteBatchedInserts} parameter, so
     * there is nothing to recommend; overrides {@link PostgresSupport#batchRewriteUrlParameter()}
     * back to {@code null}.
     *
     * @return {@code null}
     * @since 2.10.0
     */
    @Override
    public String batchRewriteUrlParameter() {
        return null;
    }

    /**
     * Constraint reading is PostgreSQL-specific (issue #479 first cut); CockroachDB's catalog differs,
     * so it overrides {@link PostgresSupport}'s reader back to none.
     *
     * @return an empty map
     * @since 2.14.0
     */
    @Override
    public java.util.Map<String, io.bloviate.db.ColumnConstraint> readConstraints(java.sql.Connection connection, String schema, String table) {
        return java.util.Map.of();
    }
}
