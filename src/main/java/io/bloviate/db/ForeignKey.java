package io.bloviate.db;

public class ForeignKey {

    private final Column column;
    private final String foreignTable;
    private final Column foreignKey;


    public ForeignKey(Column column, String foreignTable, Column foreignKey) {
        this.column = column;
        this.foreignTable = foreignTable;
        this.foreignKey = foreignKey;
    }

    public Column getColumn() {
        return column;
    }

    public String getForeignTable() {
        return foreignTable;
    }

    public Column getForeignKey() {
        return foreignKey;
    }
}
