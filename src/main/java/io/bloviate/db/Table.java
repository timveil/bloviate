package io.bloviate.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

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

    protected List<Column> getColumns() {
        return columns;
    }

    public String insertString() {
        StringJoiner nameJoiner = new StringJoiner(",");
        StringJoiner valueJoiner = new StringJoiner(",");

        for (Column column : filteredColumns()) {
            nameJoiner.add(column.name());
            valueJoiner.add("?");
        }

        return String.format("insert into %s (%s) values (%s)", name, nameJoiner, valueJoiner);

    }

    public List<Column> filteredColumns() {
        List<Column> filtered = new ArrayList<>();

        for (Column column : columns) {
            if (!column.autoIncrement()) {
                filtered.add(column);
            }
        }

        return filtered;
    }

    public PrimaryKey getPrimaryKey() {
        return primaryKey;
    }

    public List<ForeignKey> getForeignKeys() {
        return foreignKeys;
    }

    public Column findColumn(String name) {
        for (Column column : columns) {
            if (column.name().equalsIgnoreCase(name)) {
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
