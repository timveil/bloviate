package io.bloviate.db;

public class PrimaryKey {

    private final Column column;

    public PrimaryKey(Column column) {
        this.column = column;
    }

    public Column getColumn() {
        return column;
    }
}
