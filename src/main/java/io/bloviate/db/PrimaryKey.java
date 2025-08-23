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
 * Represents a primary key constraint in a database table.
 * A primary key uniquely identifies each record in a table and can consist
 * of one or more columns (composite key).
 *
 * @param tableName the name of the table containing this primary key
 * @param keyColumns the ordered list of columns that comprise this primary key
 * @since 1.0.0
 */
public record PrimaryKey(String tableName, List<KeyColumn> keyColumns) {}
