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

import io.bloviate.db.metadata.Column;
import io.bloviate.gen.*;

import java.math.BigDecimal;
import java.sql.*;

public abstract class AbstractDatabaseSupport implements DatabaseSupport {

    @Override
    public final DataGenerator getDataGenerator(Column column) {
        switch (column.jdbcType()) {
            case BIT -> {
                return buildBitGenerator(column);
            }
            case TINYINT -> {
                return buildTinyIntGenerator(column);
            }
            case SMALLINT -> {
                return buildSmallIntGenerator(column);
            }
            case INTEGER -> {
                return buildIntegerGenerator(column);
            }
            case BIGINT -> {
                return buildBigIntGenerator(column);
            }
            case FLOAT -> {
                return buildFloatGenerator(column);
            }
            case REAL -> {
                return buildRealGenerator(column);
            }
            case DOUBLE -> {
                return buildDoubleGenerator(column);
            }
            case NUMERIC -> {
                return buildNumericGenerator(column);
            }
            case DECIMAL -> {
                return buildDecimalGenerator(column);
            }
            case CHAR -> {
                return buildCharGenerator(column);
            }
            case VARCHAR -> {
                return buildVarcharGenerator(column);
            }
            case LONGVARCHAR -> {
                return buildLongVarcharGenerator(column);
            }
            case DATE -> {
                return buildDateGenerator(column);
            }
            case TIME -> {
                return buildTimeGenerator(column);
            }
            case TIMESTAMP -> {
                return buildTimestampGenerator(column);
            }
            case BINARY -> {
                return buildBinaryGenerator(column);
            }
            case VARBINARY -> {
                return buildVarbinaryGenerator(column);
            }
            case LONGVARBINARY -> {
                return buildLongVarbinaryGenerator(column);
            }
            case NULL -> {
                return buildNullGenerator(column);
            }
            case OTHER -> {
                return buildOtherGenerator(column);
            }
            case JAVA_OBJECT -> {
                return buildJavaObjectGenerator(column);
            }
            case DISTINCT -> {
                return buildDistinctGenerator(column);
            }
            case STRUCT -> {
                return buildStructGenerator(column);
            }
            case ARRAY -> {
                return buildArrayGenerator(column);
            }
            case BLOB -> {
                return buildBlobGenerator(column);
            }
            case CLOB -> {
                return buildClobGenerator(column);
            }
            case REF -> {
                return buildRefGenerator(column);
            }
            case DATALINK -> {
                return buildDataLinkGenerator(column);
            }
            case BOOLEAN -> {
                return buildBooleanGenerator(column);
            }
            case ROWID -> {
                return buildRowIdGenerator(column);
            }
            case NCHAR -> {
                return buildNCharGenerator(column);
            }
            case NVARCHAR -> {
                return buildNVarcharGenerator(column);
            }
            case LONGNVARCHAR -> {
                return buildLongNVarcharGenerator(column);
            }
            case NCLOB -> {
                return buildNClobGenerator(column);
            }
            case SQLXML -> {
                return buildSqlXmlGenerator(column);
            }
            case REF_CURSOR -> {
                return buildRefCursorGenerator(column);
            }
            case TIME_WITH_TIMEZONE -> {
                return buildTimeWithTimezoneGenerator(column);
            }
            case TIMESTAMP_WITH_TIMEZONE -> {
                return buildTimestampWithTimezoneGenerator(column);
            }
        }

        throw new UnsupportedOperationException("JDBCType [" + column.jdbcType() + "] not supported");
    }

    @Override
    public DataGenerator<Short> buildTinyIntGenerator(Column column) {
        return new ShortGenerator.Builder().start(0).end(255).build();
    }

    @Override
    public DataGenerator<Short> buildSmallIntGenerator(Column column) {
        return new ShortGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Integer> buildIntegerGenerator(Column column) {
        return new IntegerGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Long> buildBigIntGenerator(Column column) {
        return new LongGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Float> buildFloatGenerator(Column column) {
        return new FloatGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Float> buildRealGenerator(Column column) {
        return new FloatGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Double> buildDoubleGenerator(Column column) {
        return new DoubleGenerator.Builder().maxDigits(column.maxDigits()).build();
    }

    @Override
    public DataGenerator<BigDecimal> buildDecimalGenerator(Column column) {
        return new BigDecimalGenerator.Builder().precision(column.maxSize()).digits(column.maxDigits()).build();
    }

    @Override
    public DataGenerator<String> buildCharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<String> buildNCharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<String> buildVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<String> buildNVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<String> buildLongVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<String> buildLongNVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<Date> buildDateGenerator(Column column) {
        return new SqlDateGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Time> buildTimeGenerator(Column column) {
        return new SqlTimeGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Time> buildTimeWithTimezoneGenerator(Column column) {
        return new SqlTimeGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Timestamp> buildTimestampGenerator(Column column) {
        return new SqlTimestampGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Timestamp> buildTimestampWithTimezoneGenerator(Column column) {
        return new SqlTimestampGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Byte[]> buildBinaryGenerator(Column column) {
        return new ByteGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<Byte[]> buildVarbinaryGenerator(Column column) {
        return new ByteGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<Byte[]> buildLongVarbinaryGenerator(Column column) {
        return new ByteGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<Blob> buildBlobGenerator(Column column) {
        return new SqlBlobGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Clob> buildClobGenerator(Column column) {
        return new SqlClobGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Clob> buildNClobGenerator(Column column) {
        return new SqlClobGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Struct> buildStructGenerator(Column column) {
        return new SqlStructGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Object> buildArrayGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator buildBitGenerator(Column column) {
        if (1 == column.maxSize()) {
            return new BitGenerator.Builder().build();
        } else {
            return new BitStringGenerator.Builder().size(column.maxSize()).build();
        }
    }

    @Override
    public DataGenerator<Boolean> buildBooleanGenerator(Column column) {
        return new BooleanGenerator.Builder().build();
    }

    @Override
    public DataGenerator<Object> buildOtherGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<BigDecimal> buildNumericGenerator(Column column) {
        return new BigDecimalGenerator.Builder().precision(column.maxSize()).digits(column.maxDigits()).build();
    }

    @Override
    public DataGenerator<Object> buildJavaObjectGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildDistinctGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildNullGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildRefGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildDataLinkGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildRowIdGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildSqlXmlGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<Object> buildRefCursorGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }
}
