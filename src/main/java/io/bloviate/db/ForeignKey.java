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

import java.util.List;

/**
 * Represents a foreign key constraint in a database table.
 * A foreign key establishes a link between data in two tables by referencing
 * the primary key of another table.
 *
 * @param foreignKeyColumns the columns that make up the foreign key
 * @param primaryKey the primary key that this foreign key references
 * @since 1.0.0
 */
public record ForeignKey(List<KeyColumn> foreignKeyColumns, PrimaryKey primaryKey) {}
