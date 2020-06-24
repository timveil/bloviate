package io.bloviate.db;

import java.util.List;
import java.util.Objects;


public class PrimaryKey {

    private final String tableName;
    private final List<KeyColumn> keyColumns;

    public PrimaryKey(String tableName, List<KeyColumn> keyColumns) {
        this.tableName = tableName;
        this.keyColumns = keyColumns;
    }

    public String getTableName() {
        return tableName;
    }

    public List<KeyColumn> getKeyColumns() {
        return keyColumns;
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
        return Objects.equals(tableName, that.tableName) &&
                Objects.equals(keyColumns, that.keyColumns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, keyColumns);
    }

    @Override
    public String toString() {
        return "PrimaryKey{" +
                "tableName='" + tableName + '\'' +
                ", keyColumns=" + keyColumns +
                '}';
    }
}
