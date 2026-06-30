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
import io.bloviate.ext.GeneratorRegistry;

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
 *   <li>An optional registry of custom generator rules</li>
 *   <li>A base seed for reproducible, controllable data generation</li>
 * </ul>
 *
 * <p>The {@code seed} makes a fill <strong>reproducible</strong>: the same schema filled with
 * the same seed always produces identical data, and changing the seed produces a different but
 * equally reproducible dataset. See
 * {@link io.bloviate.util.DatabaseUtils#columnSeed(Column, long)} for how per-column seeds are
 * derived.
 *
 * @param batchSize the number of rows to include in each batch INSERT operation; must be {@code >= 1}
 * @param defaultRowCount the default number of rows to generate for each table
 * @param databaseSupport the database-specific support implementation
 * @param tableConfigurations optional per-table configuration overrides
 * @param generatorRegistry optional registry of custom generator rules (by column-name pattern,
 *        vendor type name, or JDBCType), consulted between per-column overrides and the
 *        {@code databaseSupport} defaults; may be null
 * @param seed the base seed for reproducible generation; vary it for a different dataset
 * @param commitStrategy how the engine commits inserted rows; defaults to
 *        {@link CommitStrategy#connectionDefault()} (autocommit untouched) when null
 * @param bulkLoadStrategy how the engine orders fills relative to foreign-key dependencies; defaults to
 *        {@link BulkLoadStrategy#ordered()} (dependency-ordered) when null
 *
 * @author Tim Veil
 * @see DatabaseSupport
 * @see TableConfiguration
 * @see GeneratorRegistry
 * @see CommitStrategy
 * @see BulkLoadStrategy
 * @see DatabaseFiller
 */
public record DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, GeneratorRegistry generatorRegistry, long seed, CommitStrategy commitStrategy, BulkLoadStrategy bulkLoadStrategy) {

    /**
     * Normalizes a null {@code commitStrategy} to {@link CommitStrategy#connectionDefault()} and a null
     * {@code bulkLoadStrategy} to {@link BulkLoadStrategy#ordered()} so the back-compatible behavior
     * applies whenever a caller does not specify them.
     */
    public DatabaseConfiguration {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1: " + batchSize);
        }
        if (commitStrategy == null) {
            commitStrategy = CommitStrategy.connectionDefault();
        }
        if (bulkLoadStrategy == null) {
            bulkLoadStrategy = BulkLoadStrategy.ordered();
        }
    }

    /**
     * Creates a configuration with no custom {@link GeneratorRegistry} and a base {@code seed}
     * of {@code 0}.
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, null, 0L, null, null);
    }

    /**
     * Creates a configuration with a custom {@link GeneratorRegistry} and a base {@code seed}
     * of {@code 0}.
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     * @param generatorRegistry optional registry of custom generator rules; may be null
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, GeneratorRegistry generatorRegistry) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, generatorRegistry, 0L, null, null);
    }

    /**
     * Creates a configuration with the given base {@code seed} and no custom
     * {@link GeneratorRegistry}.
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     * @param seed the base seed for reproducible generation
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, long seed) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, null, seed, null, null);
    }

    /**
     * Creates a configuration with the given base {@code seed} and {@link CommitStrategy}, and no
     * custom {@link GeneratorRegistry}.
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     * @param seed the base seed for reproducible generation
     * @param commitStrategy how the engine commits inserted rows; may be null for the default
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, long seed, CommitStrategy commitStrategy) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, null, seed, commitStrategy, null);
    }

    /**
     * Creates a configuration with the given base {@code seed}, {@link CommitStrategy} and
     * {@link BulkLoadStrategy}, and no custom {@link GeneratorRegistry}.
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     * @param seed the base seed for reproducible generation
     * @param commitStrategy how the engine commits inserted rows; may be null for the default
     * @param bulkLoadStrategy how the engine orders fills; may be null for the default
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, long seed, CommitStrategy commitStrategy, BulkLoadStrategy bulkLoadStrategy) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, null, seed, commitStrategy, bulkLoadStrategy);
    }

    /**
     * Creates a configuration with a custom {@link GeneratorRegistry} and base {@code seed}, and the
     * default {@link CommitStrategy} (preserves the original six-argument signature).
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     * @param generatorRegistry optional registry of custom generator rules; may be null
     * @param seed the base seed for reproducible generation
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, GeneratorRegistry generatorRegistry, long seed) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, generatorRegistry, seed, null, null);
    }

    /**
     * Creates a configuration with a custom {@link GeneratorRegistry}, base {@code seed} and
     * {@link CommitStrategy}, and the default {@link BulkLoadStrategy} (preserves the original
     * seven-argument signature).
     *
     * @param batchSize the number of rows to include in each batch INSERT operation
     * @param defaultRowCount the default number of rows to generate for each table
     * @param databaseSupport the database-specific support implementation
     * @param tableConfigurations optional per-table configuration overrides
     * @param generatorRegistry optional registry of custom generator rules; may be null
     * @param seed the base seed for reproducible generation
     * @param commitStrategy how the engine commits inserted rows; may be null for the default
     */
    public DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations, GeneratorRegistry generatorRegistry, long seed, CommitStrategy commitStrategy) {
        this(batchSize, defaultRowCount, databaseSupport, tableConfigurations, generatorRegistry, seed, commitStrategy, null);
    }

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
