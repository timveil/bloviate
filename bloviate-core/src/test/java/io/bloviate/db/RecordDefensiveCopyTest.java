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

import io.bloviate.ext.H2Support;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The metadata and configuration records are shared across worker threads during parallel
 * fills, so their collection components must be defensively copied (and unmodifiable), while
 * null collections stay null for back-compatibility.
 */
class RecordDefensiveCopyTest {

    private static Column column(String name) {
        return new Column(name, "t", null, null, JDBCType.INTEGER, 10, null, "int4", false, true, null, 1);
    }

    @Test
    void tableCopiesColumnsAndForeignKeys() {
        List<Column> columns = new ArrayList<>(List.of(column("a")));
        List<ForeignKey> foreignKeys = new ArrayList<>();

        Table table = new Table("t", null, columns, foreignKeys);

        columns.add(column("b"));
        foreignKeys.add(new ForeignKey(List.of(), null));

        assertEquals(1, table.columns().size());
        assertEquals(0, table.foreignKeys().size());
        assertThrows(UnsupportedOperationException.class, () -> table.columns().add(column("c")));
    }

    @Test
    void tableToleratesNullLists() {
        Table table = new Table("t", null, null, null);

        assertNull(table.columns());
        assertNull(table.foreignKeys());
    }

    @Test
    void databaseAndKeysCopyTheirLists() {
        List<Table> tables = new ArrayList<>(List.of(new Table("t", null, List.of(column("a")), List.of())));
        Database database = new Database("h2", "2", null, null, tables);
        tables.clear();
        assertEquals(1, database.tables().size());

        List<KeyColumn> keyColumns = new ArrayList<>(List.of(new KeyColumn(1, column("a"))));
        PrimaryKey primaryKey = new PrimaryKey("t", keyColumns);
        ForeignKey foreignKey = new ForeignKey(keyColumns, primaryKey);
        keyColumns.clear();
        assertEquals(1, primaryKey.keyColumns().size());
        assertEquals(1, foreignKey.foreignKeyColumns().size());
    }

    @Test
    void configurationsCopyTheirSets() {
        Set<ColumnConfiguration> columnConfigurations = new HashSet<>();
        TableConfiguration tableConfiguration = new TableConfiguration("t", 10, columnConfigurations);
        columnConfigurations.add(new ColumnConfiguration("a", random -> null));
        assertEquals(0, tableConfiguration.columnConfigurations().size());

        Set<TableConfiguration> tableConfigurations = new HashSet<>(Set.of(tableConfiguration));
        DatabaseConfiguration configuration = new DatabaseConfiguration(10, 10, new H2Support(), tableConfigurations);
        tableConfigurations.clear();
        assertEquals(1, configuration.tableConfigurations().size());
    }

    @Test
    void configurationsTolerateNullSets() {
        assertNull(new TableConfiguration("t", 10).columnConfigurations());
        assertNull(new DatabaseConfiguration(10, 10, new H2Support(), null).tableConfigurations());
    }
}
