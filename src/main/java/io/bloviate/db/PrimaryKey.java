package io.bloviate.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class PrimaryKey {

    private static final Random RANDOM = new Random();

    private static final ReentrantLock LOCK = new ReentrantLock();

    private final Column column;

    private final List<Object> keyValues = new ArrayList<>();

    public PrimaryKey(Column column) {
        this.column = column;
    }

    public Column getColumn() {
        return column;
    }

    public void addKey(Object key) {
        LOCK.lock();
        try {
            keyValues.add(key);
        } finally {
            LOCK.unlock();
        }
    }

    public Object getRandomKey() {
        LOCK.lock();
        try {
            int size = keyValues.size();
            int randomIndex = RANDOM.nextInt(size);
            return keyValues.get(randomIndex);
        } finally {
            LOCK.unlock();
        }
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
