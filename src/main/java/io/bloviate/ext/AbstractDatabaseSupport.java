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

public abstract class AbstractDatabaseSupport implements DatabaseSupport {

    @Override
    public final DataGenerator<?> getDataGenerator(Column column) {
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
    public DataGenerator<?> buildTinyIntGenerator(Column column) {
        return new ShortGenerator.Builder().start(0).end(255).build();
    }

    @Override
    public DataGenerator<?> buildSmallIntGenerator(Column column) {
        return new ShortGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildIntegerGenerator(Column column) {
        return new IntegerGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildBigIntGenerator(Column column) {
        return new LongGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildFloatGenerator(Column column) {
        return new FloatGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildRealGenerator(Column column) {
        return new FloatGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildDoubleGenerator(Column column) {
        return new DoubleGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildDecimalGenerator(Column column) {
        return new BigDecimalGenerator.Builder().precision(column.maxSize()).digits(column.maxDigits()).build();
    }

    @Override
    public DataGenerator<?> buildCharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildNCharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildNVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildLongVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildLongNVarcharGenerator(Column column) {
        return new SimpleStringGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildDateGenerator(Column column) {
        return new SqlDateGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildTimeGenerator(Column column) {
        return new SqlTimeGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildTimeWithTimezoneGenerator(Column column) {
        return new SqlTimeGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildTimestampGenerator(Column column) {
        return new SqlTimestampGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildTimestampWithTimezoneGenerator(Column column) {
        return new SqlTimestampGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildBinaryGenerator(Column column) {
        return new ByteGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildVarbinaryGenerator(Column column) {
        return new ByteGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildLongVarbinaryGenerator(Column column) {
        return new ByteGenerator.Builder().size(column.maxSize()).build();
    }

    @Override
    public DataGenerator<?> buildBlobGenerator(Column column) {
        return new SqlBlobGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildClobGenerator(Column column) {
        return new SqlClobGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildNClobGenerator(Column column) {
        return new SqlClobGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildStructGenerator(Column column) {
        return new SqlStructGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildArrayGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildBitGenerator(Column column) {
        if (1 == column.maxSize()) {
            return new BitGenerator.Builder().build();
        } else {
            return new BitStringGenerator.Builder().size(column.maxSize()).build();
        }
    }

    @Override
    public DataGenerator<?> buildBooleanGenerator(Column column) {
        return new BooleanGenerator.Builder().build();
    }

    @Override
    public DataGenerator<?> buildOtherGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildNumericGenerator(Column column) {
        return new BigDecimalGenerator.Builder().precision(column.maxSize()).digits(column.maxDigits()).build();
    }

    @Override
    public DataGenerator<?> buildJavaObjectGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildDistinctGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildNullGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildRefGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildDataLinkGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildRowIdGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildSqlXmlGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildRefCursorGenerator(Column column) {
        throw new UnsupportedOperationException("generator not supported");
    }
}
