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
 * Represents a database instance with its metadata and contained tables.
 * 
 * <p>This immutable record encapsulates database-level information including
 * the database product name, version, catalog, schema, and all tables within
 * the database. It provides convenient access to tables by name with
 * case-insensitive lookup.
 * 
 * <p>Database instances are typically created by {@link io.bloviate.util.DatabaseUtils}
 * when analyzing database metadata through JDBC.
 * 
 * @param product the database product name (e.g., "PostgreSQL", "MySQL")
 * @param productVersion the version string of the database product
 * @param catalog the catalog name, may be null for databases that don't use catalogs
 * @param schema the schema name, may be null for databases that don't use schemas
 * @param tables the list of tables contained in this database
 * 
 * @author Tim Veil
 * @see Table
 * @see io.bloviate.util.DatabaseUtils
 */
public record Database(String product, String productVersion, String catalog, String schema, List<Table> tables) {

    /**
     * Retrieves a table by name using case-insensitive comparison.
     * 
     * @param tableName the name of the table to find
     * @return the table with the specified name
     * @throws IllegalArgumentException if no table with the given name exists
     */
    public Table getTable(String tableName) {
        for (Table table : tables) {
            if (table.name().equalsIgnoreCase(tableName)) {
                return table;
            }
        }

        throw new IllegalArgumentException(String.format("table with name [%s] not found", tableName));
    }
}
