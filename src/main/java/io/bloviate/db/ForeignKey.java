package io.bloviate.db;

import java.util.Objects;

public class ForeignKey {

    // FOREIGN KEY (COLUMN_A) REFERENCES TABLE_B (COLUMN_B)

    // the column on the current table: COLUMN_A
    private final Column column;

    // the table referenced by the key: TABLE_B
    private final String foreignTable;

    // the column on the referenced table: COLUMN_B
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ForeignKey that = (ForeignKey) o;
        return Objects.equals(column, that.column) &&
                Objects.equals(foreignTable, that.foreignTable) &&
                Objects.equals(foreignKey, that.foreignKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, foreignTable, foreignKey);
    }

    @Override
    public String toString() {
        return "ForeignKey{" +
                "column=" + column.getName() +
                ", foreignTable='" + foreignTable + '\'' +
                ", foreignKey=" + foreignKey.getName() +
                '}';
    }
}
