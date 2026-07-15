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
 * The primary key of a database table — the column or columns that uniquely identify each row.
 *
 * <p>A primary key may be simple (one column) or composite (several), so it carries an ordered list
 * of {@link KeyColumn}s whose sequence positions preserve the declared key order. The fill engine
 * uses this both to recognize key columns when choosing generators and as the reference target that
 * {@link ForeignKey}s point at.
 *
 * @param tableName the name of the table containing this primary key
 * @param keyColumns the ordered list of columns that comprise this primary key
 * @since 1.0.0
 */
public record PrimaryKey(String tableName, List<KeyColumn> keyColumns) {

    /** Copies the key-column list so the record is deeply immutable. */
    public PrimaryKey {
        keyColumns = keyColumns == null ? null : List.copyOf(keyColumns);
    }
}
