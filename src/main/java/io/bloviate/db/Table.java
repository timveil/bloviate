package io.bloviate.db;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public record Table(String name, PrimaryKey primaryKey, List<Column> columns, List<ForeignKey> foreignKeys) {

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

    public Column findColumn(String name) {
        for (Column column : columns) {
            if (column.name().equalsIgnoreCase(name)) {
                return column;
            }
        }

        return null;
    }

}
