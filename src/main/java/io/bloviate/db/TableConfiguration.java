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

import java.util.Set;

/**
 * Configuration for customizing how a specific table should be filled with data.
 * This record allows users to specify a custom row count for an individual table
 * (overriding the default row count specified in the DatabaseConfiguration) and,
 * optionally, per-column generator overrides.
 *
 * @param tableName the name of the table to configure
 * @param rowCount the number of rows to generate for this table
 * @param columnConfigurations optional per-column generator overrides; may be null or empty
 * @since 1.0.0
 */
public record TableConfiguration(String tableName, long rowCount, Set<ColumnConfiguration> columnConfigurations) {

    /**
     * Creates a table configuration with no per-column overrides.
     *
     * @param tableName the name of the table to configure
     * @param rowCount the number of rows to generate for this table
     */
    public TableConfiguration(String tableName, long rowCount) {
        this(tableName, rowCount, null);
    }

    /**
     * Retrieves the per-column configuration for the given column name.
     *
     * @param columnName the name of the column (matched case-insensitively)
     * @return the column configuration if one is defined, or null otherwise
     */
    public ColumnConfiguration columnConfiguration(String columnName) {
        if (columnConfigurations != null) {
            for (ColumnConfiguration columnConfiguration : columnConfigurations) {
                if (columnConfiguration.columnName().equalsIgnoreCase(columnName)) {
                    return columnConfiguration;
                }
            }
        }

        return null;
    }
}
