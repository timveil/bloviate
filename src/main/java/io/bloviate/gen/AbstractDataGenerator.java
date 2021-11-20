package io.bloviate.gen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

public abstract class AbstractDataGenerator<T> implements DataGenerator<T> {

    final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Random random;

    public AbstractDataGenerator(Random random) {
        this.random = random;
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, T value) throws SQLException {
        statement.setObject(parameterIndex, value);
    }

    @Override
    public String toString(T generatedValue) {

        if (generatedValue != null) {
            return generatedValue.toString();
        }

        return null;
    }

}
