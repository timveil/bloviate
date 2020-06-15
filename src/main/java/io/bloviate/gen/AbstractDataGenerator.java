package io.bloviate.gen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class AbstractDataGenerator<T> implements DataGenerator<T> {

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex) throws SQLException {
        statement.setObject(parameterIndex, generate());
    }
}
