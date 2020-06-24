package io.bloviate.db;

import java.util.Objects;

public class KeyColumn {

    private final int sequence;
    private final Column column;

    public KeyColumn(int sequence, Column column) {
        this.sequence = sequence;
        this.column = column;
    }

    public int getSequence() {
        return sequence;
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
        KeyColumn keyColumn = (KeyColumn) o;
        return sequence == keyColumn.sequence &&
                Objects.equals(column, keyColumn.column);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence, column);
    }
}
