package io.bloviate.db;

import java.util.List;
import java.util.Objects;

public class ForeignKey {

    private final List<KeyColumn> foreignKeyColumns;
    private final PrimaryKey primaryKey;

    public ForeignKey(List<KeyColumn> foreignKeyColumns, PrimaryKey primaryKey) {
        this.foreignKeyColumns = foreignKeyColumns;
        this.primaryKey = primaryKey;
    }

    public List<KeyColumn> getForeignKeyColumns() {
        return foreignKeyColumns;
    }

    public PrimaryKey getPrimaryKey() {
        return primaryKey;
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
        return Objects.equals(foreignKeyColumns, that.foreignKeyColumns) &&
                Objects.equals(primaryKey, that.primaryKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foreignKeyColumns, primaryKey);
    }

    @Override
    public String toString() {
        return "ForeignKey{" +
                "foreignKeyColumns=" + foreignKeyColumns +
                ", primaryKey=" + primaryKey +
                '}';
    }
}
