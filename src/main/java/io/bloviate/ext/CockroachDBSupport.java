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

}
