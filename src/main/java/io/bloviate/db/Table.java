package io.bloviate.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Table {

    private final String name;
    private final List<Column> columns;
    private final PrimaryKey primaryKey;
    private final List<ForeignKey> foreignKeys;

    public Table(String name, PrimaryKey primaryKey, List<Column> columns, List<ForeignKey> foreignKeys) {
        this.name = name;
        this.columns = columns;
        this.primaryKey = primaryKey;
        this.foreignKeys = foreignKeys;
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<Column> filterColumns(boolean excludeAutoIncrement) {
        List<Column> filtered = new ArrayList<>();

        for (Column column : columns) {
            if (excludeAutoIncrement) {
                if (!column.getAutoIncrement()) {
                    filtered.add(column);
                }
            } else {
                filtered.add(column);
            }
        }

        return filtered;
    }


    public boolean partOfPrimaryKey(Column column) {
        if (primaryKey != null) {
            for (KeyColumn kc : primaryKey.getKeyColumns()) {
                if (kc.getColumn().equals(column)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean partOfForeignKeys(Column column) {
        if (foreignKeys != null) {
            for (ForeignKey fk : foreignKeys) {
                for (KeyColumn kc : fk.getForeignKeyColumns()) {
                    if (kc.getColumn().equals(column)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    public PrimaryKey getPrimaryKey() {
        return primaryKey;
    }

    public List<ForeignKey> getForeignKeys() {
        return foreignKeys;
    }

    public Column findColumn(String name) {
        for (Column column : columns) {
            if (column.getName().equalsIgnoreCase(name)) {
                return column;
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Table table = (Table) o;
        return Objects.equals(name, table.name) &&
                Objects.equals(columns, table.columns) &&
                Objects.equals(primaryKey, table.primaryKey) &&
                Objects.equals(foreignKeys, table.foreignKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, columns, primaryKey, foreignKeys);
    }

    @Override
    public String toString() {
        return "Table{" +
                "name='" + name + '\'' +
                ", columns=" + columns +
                ", primaryKey=" + primaryKey +
                ", foreignKeys=" + foreignKeys +
                '}';
    }
}
