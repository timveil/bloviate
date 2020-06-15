package io.bloviate.db;

import java.util.Objects;

public class PrimaryKey {

    private final Column column;

    public PrimaryKey(Column column) {
        this.column = column;
    }

    public Column getColumn() {
        return column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrimaryKey that = (PrimaryKey) o;
        return Objects.equals(column, that.column);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column);
    }

    @Override
    public String toString() {
        return "PrimaryKey{" +
                "column=" + column.getName() +
                '}';
    }
}
