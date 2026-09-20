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
 * A foreign-key relationship: the columns of one table that reference the {@link PrimaryKey} of
 * another.
 *
 * <p>This is the edge that drives the fill engine's topological ordering — the referenced (parent)
 * table must be populated before the table that depends on it, so {@link DatabaseFiller} builds its
 * dependency graph from these relationships. It also lets {@link TableFiller} seed a foreign-key
 * column from its associated primary-key column so the generated values line up. Like a primary key,
 * a foreign key may span several columns, paired in order with the primary-key columns they
 * reference.
 *
 * @param foreignKeyColumns the columns that make up the foreign key
 * @param primaryKey the primary key that this foreign key references
 * @param referencedSchema the schema of the referenced table when it is not the schema the owning table
 *        was read from, otherwise null. A non-null value means {@code primaryKey} names a table in
 *        <em>another</em> schema: it is not the same-named table (if any) of this schema, and its key
 *        columns are not read.
 * @param referencedCatalog the catalog of the referenced table when it is not the catalog the owning
 *        table was read from, otherwise null; see {@code referencedSchema}
 * @since 1.0.0
 */
public record ForeignKey(List<KeyColumn> foreignKeyColumns, PrimaryKey primaryKey, String referencedSchema, String referencedCatalog) {

    /** Copies the key-column list so the record is deeply immutable. */
    public ForeignKey {
        foreignKeyColumns = foreignKeyColumns == null ? null : List.copyOf(foreignKeyColumns);
    }

    /**
     * Creates a foreign key to a table in the same schema and catalog as the owning table.
     *
     * @param foreignKeyColumns the columns that make up the foreign key
     * @param primaryKey the primary key that this foreign key references
     */
    public ForeignKey(List<KeyColumn> foreignKeyColumns, PrimaryKey primaryKey) {
        this(foreignKeyColumns, primaryKey, null, null);
    }

    /**
     * Whether the referenced table lives in another schema or catalog than the owning table.
     *
     * @return true if the referenced table is outside the schema and catalog the owning table was read from
     * @since 3.3.0
     */
    public boolean referencesOtherSchema() {
        return referencedSchema != null || referencedCatalog != null;
    }
}
