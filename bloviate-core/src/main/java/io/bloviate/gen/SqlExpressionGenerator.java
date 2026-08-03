/*
 * Copyright (c) 2021 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.gen;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wraps another {@link DataGenerator} so its value is constructed by a SQL expression rather than
 * bound as a bare placeholder, changing nothing else about it.
 *
 * <p>This is how a shared generator is reused for a type whose value the driver cannot bind
 * directly. {@link JsonbGenerator} already produces exactly the JSON text a BigQuery {@code JSON}
 * column needs, but the driver binds it as a {@code STRING} and BigQuery will not coerce that into
 * {@code JSON}; wrapping supplies the missing {@code PARSE_JSON(?)} without giving
 * {@code JsonbGenerator} a BigQuery-specific opinion that would then apply to PostgreSQL too:
 *
 * <pre>{@code
 * SqlExpressionGenerator.of(new JsonbGenerator.Builder(random).build(), "PARSE_JSON(?)")
 * }</pre>
 *
 * <p>Use {@link #of(DataGenerator, String)} rather than the constructor. Whether a generator
 * implements {@link IndexedDataGenerator} is load-bearing &mdash; the fill engine tests for it to
 * decide how to position a partitioned fill &mdash; so the factory returns a variant that
 * implements it exactly when the delegate does. A single class cannot do that, and getting it wrong
 * in either direction breaks partitioned fills: claiming the interface without a seekable delegate
 * silently skips repositioning, and dropping it from a seekable delegate replays draws instead of
 * seeking.
 *
 * @param <T> the Java type of values produced by the wrapped generator
 * @since 3.2.0
 * @see DataGenerator#valueExpression()
 */
public class SqlExpressionGenerator<T> implements DataGenerator<T> {

    private final DataGenerator<T> delegate;
    private final String valueExpression;

    /**
     * Wraps a generator so its value is written through the given SQL expression.
     *
     * @param delegate        the generator producing the value; must not be null
     * @param valueExpression the SQL expression, containing exactly one {@code ?}
     * @param <T>             the generated value type
     * @return a generator equivalent to {@code delegate} but reporting {@code valueExpression},
     *         implementing {@link IndexedDataGenerator} exactly when {@code delegate} does
     * @throws IllegalArgumentException if the expression does not contain exactly one {@code ?}
     */
    public static <T> DataGenerator<T> of(DataGenerator<T> delegate, String valueExpression) {
        return delegate instanceof IndexedDataGenerator
                ? new IndexedSqlExpressionGenerator<>(delegate, valueExpression)
                : new SqlExpressionGenerator<>(delegate, valueExpression);
    }

    /**
     * Prefer {@link #of(DataGenerator, String)}, which preserves a seekable delegate's
     * {@link IndexedDataGenerator} contract.
     *
     * @param delegate        the generator producing the value
     * @param valueExpression the SQL expression, containing exactly one {@code ?}
     * @throws IllegalArgumentException if the expression does not contain exactly one {@code ?}
     */
    protected SqlExpressionGenerator(DataGenerator<T> delegate, String valueExpression) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate generator is required");
        }
        this.delegate = delegate;
        this.valueExpression = validate(valueExpression);
    }

    private static String validate(String valueExpression) {
        if (valueExpression == null) {
            throw new IllegalArgumentException("value expression is required");
        }
        int placeholders = 0;
        for (int i = 0; i < valueExpression.length(); i++) {
            if (valueExpression.charAt(i) == '?') {
                placeholders++;
            }
        }
        if (placeholders != 1) {
            throw new IllegalArgumentException(String.format(
                    "value expression [%s] contains %d '?' placeholders; it must contain exactly one, "
                            + "because the engine binds one parameter per column by position",
                    valueExpression, placeholders));
        }
        return valueExpression;
    }

    /** @return the wrapped generator */
    protected final DataGenerator<T> delegate() {
        return delegate;
    }

    @Override
    public final String valueExpression() {
        return valueExpression;
    }

    @Override
    public T generate() {
        return delegate.generate();
    }

    @Override
    public String generateAsString() {
        return delegate.generateAsString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegated rather than assumed: wrapping changes only how the value reaches the column, so
     * the delegate's positioning contract carries over unchanged. Answering for it would either
     * skip repositioning a positionable column or reposition one that cannot survive it.
     */
    @Override
    public boolean positionable() {
        return delegate.positionable();
    }

    @Override
    public void reseed(long seed) {
        delegate.reseed(seed);
    }

    @Override
    public void generateAndSet(Connection connection, PreparedStatement statement, int parameterIndex)
            throws SQLException {
        delegate.generateAndSet(connection, statement, parameterIndex);
    }

    @Override
    public void set(Connection connection, PreparedStatement statement, int parameterIndex, T value)
            throws SQLException {
        delegate.set(connection, statement, parameterIndex, value);
    }

    @Override
    public T get(ResultSet resultSet, int columnIndex) throws SQLException {
        return delegate.get(resultSet, columnIndex);
    }

    /**
     * The variant returned by {@link #of} for a delegate that is itself an
     * {@link IndexedDataGenerator}, so a partitioned fill still seeks the delegate's counter to the
     * absolute row index instead of replaying draws.
     *
     * @param <T> the generated value type
     */
    private static final class IndexedSqlExpressionGenerator<T> extends SqlExpressionGenerator<T>
            implements IndexedDataGenerator {

        private IndexedSqlExpressionGenerator(DataGenerator<T> delegate, String valueExpression) {
            super(delegate, valueExpression);
        }

        @Override
        public void seek(long rowIndex) {
            ((IndexedDataGenerator) delegate()).seek(rowIndex);
        }
    }
}
