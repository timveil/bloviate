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

import io.bloviate.ext.DatabaseSupport;

import java.util.Set;

/**
 * Configuration settings for database filling operations.
 * 
 * <p>This record encapsulates all configuration parameters needed to control
 * the database filling process, including batch sizes, row counts, database-specific
 * support, and table-specific overrides.
 * 
 * <p>The configuration supports:
 * <ul>
 *   <li>Global batch size for INSERT operations</li>
 *   <li>Default row count applied to all tables</li>
 *   <li>Database-specific support implementation</li>
 *   <li>Per-table configuration overrides</li>
 * </ul>
 * 
 * @param batchSize the number of rows to include in each batch INSERT operation
 * @param defaultRowCount the default number of rows to generate for each table
 * @param databaseSupport the database-specific support implementation
 * @param tableConfigurations optional per-table configuration overrides
 * 
 * @author Tim Veil
 * @see DatabaseSupport
 * @see TableConfiguration
 * @see DatabaseFiller
 */
public record DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations) {

    /**
     * Retrieves the table-specific configuration for the given table name.
     * 
     * @param tableName the name of the table to find configuration for
     * @return the table configuration if found, or null if using defaults
     */
    public TableConfiguration tableConfiguration(String tableName) {
        if (tableConfigurations != null && !tableConfigurations.isEmpty()) {
            for (TableConfiguration tableConfiguration : tableConfigurations) {
                if (tableConfiguration.tableName().equalsIgnoreCase(tableName)) {
                    return tableConfiguration;
                }
            }
        }

        return null;
    }
}
