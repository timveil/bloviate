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
import java.util.random.RandomGenerator;

/**
 * Base {@link DatabaseSupport} that maps columns to generators through a
 * {@link JDBCType}-keyed registry of {@link GeneratorFactory} entries.
 *
 * <p>On first use the registry is seeded with cross-database defaults and then
 * {@link #configure(Map)} is invoked, which database-specific subclasses override to add or
 * replace entries (for example, to handle driver-specific type names). Types absent from the
 * registry are unsupported and cause {@link #getDataGenerator(Column, RandomGenerator)} to throw.
 *
 * @see GeneratorFactory
 */
public abstract class AbstractDatabaseSupport implements DatabaseSupport {

    // built lazily on first use (double-checked): calling the overridable configure() from the
    // constructor would hand a subclass the registry before its own fields are initialized — a
    // fragile-base-class trap for third-party DatabaseSupport implementations
    private volatile Map<JDBCType, GeneratorFactory> registry;

    /** Creates the support; the generator registry is built on first use. */
    protected AbstractDatabaseSupport() {
    }

    private Map<JDBCType, GeneratorFactory> registry() {
        Map<JDBCType, GeneratorFactory> result = registry;
        if (result == null) {
            synchronized (this) {
                result = registry;
                if (result == null) {
                    Map<JDBCType, GeneratorFactory> defaults = new EnumMap<>(JDBCType.class);
                    registerDefaults(defaults);
                    configure(defaults);
                    registry = result = defaults;
                }
            }
        }
        return result;
    }

    @Override
    public final DataGenerator<?> getDataGenerator(Column column, RandomGenerator random) {
        GeneratorFactory factory = registry().get(column.jdbcType());
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
            // COLUMN_SIZE is nullable metadata; an unreported size is treated as the SQL
            // default of BIT(1), matching how a bare BIT column is declared
            Integer maxSize = column.maxSize();
            if (maxSize == null || maxSize == 1) {
                return new BitGenerator.Builder(random).build();
            }
            return new BitStringGenerator.Builder(random).size(maxSize).build();
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

        GeneratorFactory string = (column, random) -> {
            // COLUMN_SIZE is nullable metadata; fall back to the generator's default length
            Integer maxSize = column.maxSize();
            SimpleStringGenerator.Builder builder = new SimpleStringGenerator.Builder(random);
            if (maxSize != null && maxSize > 0) {
                builder.size(maxSize);
            }
            return builder.build();
        };
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

        GeneratorFactory bytes = (column, random) -> {
            // COLUMN_SIZE is nullable metadata; fall back to the generator's default length
            Integer maxSize = column.maxSize();
            ByteGenerator.Builder builder = new ByteGenerator.Builder(random);
            if (maxSize != null && maxSize > 0) {
                builder.size(maxSize);
            }
            return builder.build();
        };
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
