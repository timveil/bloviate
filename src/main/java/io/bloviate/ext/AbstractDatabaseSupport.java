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

package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.gen.*;

import java.sql.JDBCType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Base {@link DatabaseSupport} that maps columns to generators through a
 * {@link JDBCType}-keyed registry of {@link GeneratorFactory} entries.
 *
 * <p>The constructor seeds the registry with cross-database defaults and then invokes
 * {@link #configure(Map)}, which database-specific subclasses override to add or replace
 * entries (for example, to handle driver-specific type names). Types absent from the
 * registry are unsupported and cause {@link #getDataGenerator(Column, Random)} to throw.
 *
 * @see GeneratorFactory
 */
public abstract class AbstractDatabaseSupport implements DatabaseSupport {

    private final Map<JDBCType, GeneratorFactory> registry;

    protected AbstractDatabaseSupport() {
        Map<JDBCType, GeneratorFactory> defaults = new EnumMap<>(JDBCType.class);
        registerDefaults(defaults);
        configure(defaults);
        this.registry = defaults;
    }

    @Override
    public final DataGenerator<?> getDataGenerator(Column column, Random random) {
        GeneratorFactory factory = registry.get(column.jdbcType());
        if (factory == null) {
            throw new UnsupportedOperationException("JDBCType [" + column.jdbcType() + "] not supported");
        }
        return factory.create(column, random);
    }

    /**
     * Hook for subclasses to add or replace generator factories for specific JDBC types.
     * The supplied registry already holds the cross-database defaults; subclasses with no
     * customizations need not override this method. Implementations must not retain a
     * reference to the map beyond the call.
     *
     * @param registry the mutable registry to customize, keyed by {@link JDBCType}
     */
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {
        // no customizations by default
    }

    private void registerDefaults(Map<JDBCType, GeneratorFactory> map) {
        map.put(JDBCType.BIT, (column, random) -> {
            if (1 == column.maxSize()) {
                return new BitGenerator.Builder(random).build();
            }
            return new BitStringGenerator.Builder(random).size(column.maxSize()).build();
        });

        map.put(JDBCType.TINYINT, (column, random) -> new ShortGenerator.Builder(random).start(0).end(255).build());
        map.put(JDBCType.SMALLINT, (column, random) -> new ShortGenerator.Builder(random).build());
        map.put(JDBCType.INTEGER, (column, random) -> new IntegerGenerator.Builder(random).build());
        map.put(JDBCType.BIGINT, (column, random) -> new LongGenerator.Builder(random).build());

        map.put(JDBCType.FLOAT, (column, random) -> new FloatGenerator.Builder(random).build());
        map.put(JDBCType.REAL, (column, random) -> new FloatGenerator.Builder(random).build());
        map.put(JDBCType.DOUBLE, (column, random) -> new DoubleGenerator.Builder(random).build());

        GeneratorFactory bigDecimal = (column, random) ->
                new BigDecimalGenerator.Builder(random).precision(column.maxSize()).digits(column.maxDigits()).build();
        map.put(JDBCType.NUMERIC, bigDecimal);
        map.put(JDBCType.DECIMAL, bigDecimal);

        GeneratorFactory string = (column, random) ->
                new SimpleStringGenerator.Builder(random).size(column.maxSize()).build();
        map.put(JDBCType.CHAR, string);
        map.put(JDBCType.NCHAR, string);
        map.put(JDBCType.VARCHAR, string);
        map.put(JDBCType.NVARCHAR, string);
        map.put(JDBCType.LONGVARCHAR, string);
        map.put(JDBCType.LONGNVARCHAR, string);

        map.put(JDBCType.DATE, (column, random) -> new SqlDateGenerator.Builder(random).build());

        GeneratorFactory time = (column, random) -> new SqlTimeGenerator.Builder(random).build();
        map.put(JDBCType.TIME, time);
        map.put(JDBCType.TIME_WITH_TIMEZONE, time);

        GeneratorFactory timestamp = (column, random) -> new SqlTimestampGenerator.Builder(random).build();
        map.put(JDBCType.TIMESTAMP, timestamp);
        map.put(JDBCType.TIMESTAMP_WITH_TIMEZONE, timestamp);

        GeneratorFactory bytes = (column, random) ->
                new ByteGenerator.Builder(random).size(column.maxSize()).build();
        map.put(JDBCType.BINARY, bytes);
        map.put(JDBCType.VARBINARY, bytes);
        map.put(JDBCType.LONGVARBINARY, bytes);

        map.put(JDBCType.BLOB, (column, random) -> new SqlBlobGenerator.Builder(random).build());

        GeneratorFactory clob = (column, random) -> new SqlClobGenerator.Builder(random).build();
        map.put(JDBCType.CLOB, clob);
        map.put(JDBCType.NCLOB, clob);

        map.put(JDBCType.STRUCT, (column, random) -> new SqlStructGenerator.Builder(random).build());
        map.put(JDBCType.BOOLEAN, (column, random) -> new BooleanGenerator.Builder(random).build());
    }
}
