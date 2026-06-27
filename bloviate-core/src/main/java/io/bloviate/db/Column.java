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

import java.sql.JDBCType;

/**
 * Represents a database column with its complete metadata.
 * 
 * <p>This immutable record encapsulates all metadata for a database column as
 * discovered through JDBC metadata analysis. The information includes data type,
 * constraints, size limits, and positioning within the table structure.
 * 
 * <p>Column metadata is used by {@link io.bloviate.ext.DatabaseSupport} implementations
 * to select appropriate {@link io.bloviate.gen.DataGenerator} instances for generating
 * realistic test data that respects the column's constraints.
 * 
 * @param name the column name
 * @param tableName the name of the table containing this column
 * @param schema the schema name, may be null for databases that don't use schemas
 * @param catalog the catalog name, may be null for databases that don't use catalogs
 * @param jdbcType the standard JDBC data type for this column
 * @param maxSize the maximum size/length for this column, null if not applicable
 * @param maxDigits the maximum number of digits for numeric columns, null if not applicable
 * @param typeName the database-specific type name (e.g., "VARCHAR", "INTEGER")
 * @param autoIncrement true if this column is auto-increment/identity, null if unknown
 * @param nullable true if this column accepts NULL values, null if unknown
 * @param defaultValue the default value expression, null if none specified
 * @param ordinalPosition the 1-based position of this column within the table
 * 
 * @author Tim Veil
 * @see JDBCType
 * @see io.bloviate.gen.DataGenerator
 * @see io.bloviate.ext.DatabaseSupport
 */
public record Column(String name, String tableName, String schema, String catalog, JDBCType jdbcType, Integer maxSize, Integer maxDigits, String typeName, Boolean autoIncrement, Boolean nullable, String defaultValue, Integer ordinalPosition) {}
