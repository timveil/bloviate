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
 * (overriding the default row count specified in the DatabaseConfiguration), an optional
 * intra-table partition count for parallel fills, and optional per-column generator overrides.
 *
 * <p><strong>Intra-table partitioning.</strong> When {@code partitions > 1} <em>and</em> the fill
 * runs on the parallel ({@code DataSource} + {@code threads}) path, this table's rows are split into
 * that many contiguous ranges filled concurrently, one connection per range — useful when a single
 * large table dominates the fill. It is ignored on the sequential (single-{@code Connection}) path.
 *
 * <p>Partitioning is reproducible <em>for a given configuration, including the partition count</em>:
 * key columns and the columns correlated with them (foreign keys, sequences, permutations) are
 * byte-for-byte identical to a sequential fill, so foreign-key validity always holds; only plain
 * non-key random columns take different (but still deterministic) values when the partition count
 * changes. One case is unsupported: partitioning a parent table whose primary key is a plain
 * <em>random</em> generator referenced by a foreign key can orphan those references — partition the
 * child table instead, or use the positional key generators (as the bundled TPC-C/TPC-H
 * configurations do).
 *
 * @param tableName the name of the table to configure
 * @param rowCount the number of rows to generate for this table
 * @param columnConfigurations optional per-column generator overrides; may be null or empty
 * @param partitions the number of intra-table partitions for parallel fills; {@code 1} (the default)
 *        disables intra-table partitioning. Must be {@code >= 1}.
 * @since 1.0.0
 */
public record TableConfiguration(String tableName, long rowCount, Set<ColumnConfiguration> columnConfigurations, int partitions) {

    /**
     * Validates and normalizes the partition count: a non-positive value defaults to {@code 1}
     * (no intra-table partitioning).
     */
    public TableConfiguration {
        if (partitions < 1) {
            partitions = 1;
        }
    }

    /**
     * Creates a table configuration with no per-column overrides and no intra-table partitioning.
     *
     * @param tableName the name of the table to configure
     * @param rowCount the number of rows to generate for this table
     */
    public TableConfiguration(String tableName, long rowCount) {
        this(tableName, rowCount, null, 1);
    }

    /**
     * Creates a table configuration with per-column overrides and no intra-table partitioning.
     *
     * @param tableName the name of the table to configure
     * @param rowCount the number of rows to generate for this table
     * @param columnConfigurations optional per-column generator overrides; may be null or empty
     */
    public TableConfiguration(String tableName, long rowCount, Set<ColumnConfiguration> columnConfigurations) {
        this(tableName, rowCount, columnConfigurations, 1);
    }

    /**
     * Creates a table configuration with an intra-table partition count and no per-column overrides.
     *
     * @param tableName the name of the table to configure
     * @param rowCount the number of rows to generate for this table
     * @param partitions the number of intra-table partitions for parallel fills ({@code >= 1})
     */
    public TableConfiguration(String tableName, long rowCount, int partitions) {
        this(tableName, rowCount, null, partitions);
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
