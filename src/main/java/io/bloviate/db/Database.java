package io.bloviate.db;

import java.util.List;

public record Database(String catalog, String schema, List<Table> tables) {

    public Table getTable(String tableName) {
        for (Table table : tables) {
            if (table.name().equalsIgnoreCase(tableName)) {
                return table;
            }
        }

        return null;
    }
}
