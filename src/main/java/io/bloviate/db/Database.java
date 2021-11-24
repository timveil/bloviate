package io.bloviate.db;

import java.util.List;

public record Database(String product, String productVersion, String catalog, String schema, List<Table> tables) {

    public Table getTable(String tableName) {
        for (Table table : tables) {
            if (table.name().equalsIgnoreCase(tableName)) {
                return table;
            }
        }

        throw new IllegalArgumentException(String.format("table with name [%s] not found", tableName));
    }
}
