package io.bloviate.db;

import java.util.List;
import java.util.Objects;

public class Database {

    private final String catalog;
    private final String schema;
    private final List<Table> tables;

    public Database(String catalog, String schema, List<Table> tables) {
        this.catalog = catalog;
        this.schema = schema;
        this.tables = tables;
    }

    public String getCatalog() {
        return catalog;
    }

    public String getSchema() {
        return schema;
    }

    public List<Table> getTables() {
        return tables;
    }

    public Table getTable(String tableName) {
        for (Table table : tables) {
            if (table.getName().equalsIgnoreCase(tableName)) {
                return table;
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
        Database database = (Database) o;
        return Objects.equals(catalog, database.catalog) &&
                Objects.equals(schema, database.schema) &&
                Objects.equals(tables, database.tables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalog, schema, tables);
    }
}
