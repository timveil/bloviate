package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.gen.*;

import java.util.Random;

public abstract class AbstractDatabaseSupport implements DatabaseSupport {

    @Override
    public final DataGenerator<?> getDataGenerator(Column column, Random random) {
        switch (column.getJdbcType()) {
            case BIT -> {
                return buildBitGenerator(column, random);
            }
            case TINYINT -> {
                return buildTinyIntGenerator(column, random);
            }
            case SMALLINT -> {
                return buildSmallIntGenerator(column, random);
            }
            case INTEGER -> {
                return buildIntegerGenerator(column, random);
            }
            case BIGINT -> {
                return buildBigIntGenerator(column, random);
            }
            case FLOAT -> {
                return buildFloatGenerator(column, random);
            }
            case REAL -> {
                return buildRealGenerator(column, random);
            }
            case DOUBLE -> {
                return buildDoubleGenerator(column, random);
            }
            case NUMERIC -> {
                return buildNumericGenerator(column, random);
            }
            case DECIMAL -> {
                return buildDecimalGenerator(column, random);
            }
            case CHAR -> {
                return buildCharGenerator(column, random);
            }
            case VARCHAR -> {
                return buildVarcharGenerator(column, random);
            }
            case LONGVARCHAR -> {
                return buildLongVarcharGenerator(column, random);
            }
            case DATE -> {
                return buildDateGenerator(column, random);
            }
            case TIME -> {
                return buildTimeGenerator(column, random);
            }
            case TIMESTAMP -> {
                return buildTimestampGenerator(column, random);
            }
            case BINARY -> {
                return buildBinaryGenerator(column, random);
            }
            case VARBINARY -> {
                return buildVarbinaryGenerator(column, random);
            }
            case LONGVARBINARY -> {
                return buildLongVarbinaryGenerator(column, random);
            }
            case NULL -> {
                return buildNullGenerator(column, random);
            }
            case OTHER -> {
                return buildOtherGenerator(column, random);
            }
            case JAVA_OBJECT -> {
                return buildJavaObjectGenerator(column, random);
            }
            case DISTINCT -> {
                return buildDistinctGenerator(column, random);
            }
            case STRUCT -> {
                return buildStructGenerator(column, random);
            }
            case ARRAY -> {
                return buildArrayGenerator(column, random);
            }
            case BLOB -> {
                return buildBlobGenerator(column, random);
            }
            case CLOB -> {
                return buildClobGenerator(column, random);
            }
            case REF -> {
                return buildRefGenerator(column, random);
            }
            case DATALINK -> {
                return buildDataLinkGenerator(column, random);
            }
            case BOOLEAN -> {
                return buildBooleanGenerator(column, random);
            }
            case ROWID -> {
                return buildRowIdGenerator(column, random);
            }
            case NCHAR -> {
                return buildNCharGenerator(column, random);
            }
            case NVARCHAR -> {
                return buildNVarcharGenerator(column, random);
            }
            case LONGNVARCHAR -> {
                return buildLongNVarcharGenerator(column, random);
            }
            case NCLOB -> {
                return buildNClobGenerator(column, random);
            }
            case SQLXML -> {
                return buildSqlXmlGenerator(column, random);
            }
            case REF_CURSOR -> {
                return buildRefCursorGenerator(column, random);
            }
            case TIME_WITH_TIMEZONE -> {
                return buildTimeWithTimezoneGenerator(column, random);
            }
            case TIMESTAMP_WITH_TIMEZONE -> {
                return buildTimestampWithTimezoneGenerator(column, random);
            }
        }

        throw new UnsupportedOperationException("JDBCType [" + column.getJdbcType() + "] not supported");
    }

    @Override
    public DataGenerator<?> buildTinyIntGenerator(Column column, Random random) {
        return new ShortGenerator.Builder(random).start(0).end(255).build();
    }

    @Override
    public DataGenerator<?> buildSmallIntGenerator(Column column, Random random) {
        return new ShortGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildIntegerGenerator(Column column, Random random) {
        return new IntegerGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildBigIntGenerator(Column column, Random random) {
        return new LongGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildFloatGenerator(Column column, Random random) {
        return new FloatGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildRealGenerator(Column column, Random random) {
        return new FloatGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildDoubleGenerator(Column column, Random random) {
        return new DoubleGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildDecimalGenerator(Column column, Random random) {
        return new BigDecimalGenerator.Builder(random).precision(column.getMaxSize()).digits(column.getMaxDigits()).build();
    }

    @Override
    public DataGenerator<?> buildCharGenerator(Column column, Random random) {
        return new SimpleStringGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildNCharGenerator(Column column, Random random) {
        return new SimpleStringGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildVarcharGenerator(Column column, Random random) {
        return new SimpleStringGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildNVarcharGenerator(Column column, Random random) {
        return new SimpleStringGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildLongVarcharGenerator(Column column, Random random) {
        return new SimpleStringGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildLongNVarcharGenerator(Column column, Random random) {
        return new SimpleStringGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildDateGenerator(Column column, Random random) {
        return new SqlDateGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildTimeGenerator(Column column, Random random) {
        return new SqlTimeGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildTimeWithTimezoneGenerator(Column column, Random random) {
        return new SqlTimeGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildTimestampGenerator(Column column, Random random) {
        return new SqlTimestampGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildTimestampWithTimezoneGenerator(Column column, Random random) {
        return new SqlTimestampGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildBinaryGenerator(Column column, Random random) {
        return new ByteGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildVarbinaryGenerator(Column column, Random random) {
        return new ByteGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildLongVarbinaryGenerator(Column column, Random random) {
        return new ByteGenerator.Builder(random).size(column.getMaxSize()).build();
    }

    @Override
    public DataGenerator<?> buildBlobGenerator(Column column, Random random) {
        return new SqlBlobGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildClobGenerator(Column column, Random random) {
        return new SqlClobGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildNClobGenerator(Column column, Random random) {
        return new SqlClobGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildStructGenerator(Column column, Random random) {
        return new SqlStructGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildArrayGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildBitGenerator(Column column, Random random) {
        if (1 == column.getMaxSize()) {
            return new BitGenerator.Builder(random).build();
        } else {
            return new BitStringGenerator.Builder(random).size(column.getMaxSize()).build();
        }
    }

    @Override
    public DataGenerator<?> buildBooleanGenerator(Column column, Random random) {
        return new BooleanGenerator.Builder(random).build();
    }

    @Override
    public DataGenerator<?> buildOtherGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildNumericGenerator(Column column, Random random) {
        return new BigDecimalGenerator.Builder(random).precision(column.getMaxSize()).digits(column.getMaxDigits()).build();
    }

    @Override
    public DataGenerator<?> buildJavaObjectGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildDistinctGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildNullGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildRefGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildDataLinkGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildRowIdGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildSqlXmlGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }

    @Override
    public DataGenerator<?> buildRefCursorGenerator(Column column, Random random) {
        throw new UnsupportedOperationException("generator not supported");
    }
}
