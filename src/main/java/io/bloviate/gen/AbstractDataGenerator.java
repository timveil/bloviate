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
    public final void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        T value = generate();
        set(connection, statement, parameterIndex, value);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        statement.setObject(parameterIndex, value);
    }

    @Override
    public String generateAsString() {
        T value = generate();

        if (value != null) {
            return value.toString();
        }

        return null;
    }

}
