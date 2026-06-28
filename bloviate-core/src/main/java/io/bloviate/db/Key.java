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

/**
 * One column-level link of a foreign-key relationship between two tables.
 *
 * <p>Each instance maps a single foreign-key column to the primary-key column it references, naming
 * both tables and columns involved. For a composite foreign key the relationship is described by
 * several {@code Key} rows that share a constraint {@code name} and are ordered by {@code sequence};
 * a simple key is just one such row. This is the flat, JDBC-metadata-shaped view from which the
 * richer {@link ForeignKey}/{@link KeyColumn} structures are assembled.
 *
 * @param primaryTableName the name of the table containing the primary key
 * @param primaryColumnName the name of the primary key column
 * @param foreignTableName the name of the table containing the foreign key
 * @param foreignColumnName the name of the foreign key column
 * @param sequence the ordinal position of this column in a composite key (1-based)
 * @param name the name of the foreign key constraint
 * @since 1.0.0
 */
public record Key(String primaryTableName, String primaryColumnName, String foreignTableName, String foreignColumnName, int sequence, String name) {
}
