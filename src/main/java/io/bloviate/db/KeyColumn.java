package io.bloviate.db;

import java.util.Objects;
import java.util.Random;

public class KeyColumn {

    private final int sequence;
    private final Column column;
    private final Random random;

    public KeyColumn(int sequence, Column column) {
        this.sequence = sequence;
        this.column = column;
        this.random = new Random(column.hashCode());
    }

    public int getSequence() {
        return sequence;
    }

    public Column getColumn() {
        return column;
    }

    public Random getRandom() {
        return random;
    }

    public void resetSeed() {
        random.setSeed(column.hashCode());
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
