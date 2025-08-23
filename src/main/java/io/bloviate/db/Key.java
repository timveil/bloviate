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
 * Represents a key relationship between two database tables.
 * This record encapsulates the metadata about a foreign key constraint,
 * including the tables and columns involved in the relationship.
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
