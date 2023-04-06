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

package io.bloviate.db.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public record Table(String name, PrimaryKey primaryKey, List<Column> columns, List<ForeignKey> foreignKeys) {

//    public String insertString() {
//       return insertString(filteredColumns(false, false, true));
//    }

    public String insertString(List<Column> columns) {
        StringJoiner nameJoiner = new StringJoiner(",");
        StringJoiner valueJoiner = new StringJoiner(",");

        for (Column column : columns) {
            nameJoiner.add(column.name());
            valueJoiner.add("?");
        }

        return String.format("insert into %s (%s) values (%s)", name, nameJoiner, valueJoiner);

    }

    public String countString() {
        return String.format("select count(*) from %s", name);

    }

    public boolean hasPrimaryKey() {
        return primaryKey != null && primaryKey.keyColumns() != null && !primaryKey.keyColumns().isEmpty();
    }

    public boolean hasForeignKeys() {
        return foreignKeys != null && !foreignKeys.isEmpty();
    }


    public List<Column> filteredColumns(boolean excludePK, boolean excludeFK, boolean excludeAutoIncrement) {
        List<Column> filtered = new ArrayList<>();

        for (Column column : columns) {

            if (excludePK && isPrimaryKey(column)) {
                continue;
            }

            if (excludeFK && isForeignKey(column)) {
                continue;
            }

            if (excludeAutoIncrement && column.autoIncrement()) {
                continue;
            }

            filtered.add(column);
        }

        return filtered;
    }



    public boolean isForeignKey(Column column) {
        if (foreignKeys != null && !foreignKeys.isEmpty()) {
            for (ForeignKey fk : foreignKeys) {
                for (KeyColumn fkColumn : fk.foreignKeyColumns()) {
                    if (fkColumn.column().equals(column)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean isPrimaryKey(Column column) {
        if (primaryKey != null) {
            for (KeyColumn kc : primaryKey.keyColumns()) {
                if (kc.column().equals(column)) {
                    return true;
                }
            }
        }

        return false;
    }


    public Column findColumn(String name) {
        for (Column column : columns) {
            if (column.name().equalsIgnoreCase(name)) {
                return column;
            }
        }

        return null;
    }

}
