package io.bloviate.db;

import java.util.List;
import java.util.Objects;

public class Table {

    private final String name;
    private final List<Column> columns;
    private final List<PrimaryKey> primaryKeys;
    private final List<ForeignKey> foreignKeys;

    public Table(String name, List<Column> columns, List<PrimaryKey> primaryKeys, List<ForeignKey> foreignKeys) {
        this.name = name;
        this.columns = columns;
        this.primaryKeys = primaryKeys;
        this.foreignKeys = foreignKeys;
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<PrimaryKey> getPrimaryKeys() {
        return primaryKeys;
    }

    public List<ForeignKey> getForeignKeys() {
        return foreignKeys;
    }

    boolean isForeignKey(Column column) {
        if (foreignKeys != null) {
            return foreignKeys.contains(column);
        }

        return false;
    }

    boolean isPrimaryKey(Column column) {
        if (primaryKeys != null) {
            return primaryKeys.contains(column);
        }

        return false;
    }

    public Column getColumn(String name) {
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
                Objects.equals(primaryKeys, table.primaryKeys) &&
                Objects.equals(foreignKeys, table.foreignKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, columns, primaryKeys, foreignKeys);
    }

    @Override
    public String toString() {
        return "Table{" +
                "name='" + name + '\'' +
                ", columns=" + columns +
                ", primaryKeys=" + primaryKeys +
                ", foreignKeys=" + foreignKeys +
                '}';
    }
}
