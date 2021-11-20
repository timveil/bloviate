package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.gen.DataGenerator;

import java.util.Random;

public interface DatabaseSupport {

    <T> DataGenerator<T> getDataGenerator(Column column, Random random);

    <T> DataGenerator<T> buildTinyIntGenerator(Column column, Random random);

    <T> DataGenerator<T> buildSmallIntGenerator(Column column, Random random);

    <T> DataGenerator<T> buildIntegerGenerator(Column column, Random random);

    <T> DataGenerator<T> buildBigIntGenerator(Column column, Random random);

    <T> DataGenerator<T> buildFloatGenerator(Column column, Random random);

    <T> DataGenerator<T> buildRealGenerator(Column column, Random random);

    <T> DataGenerator<T> buildDoubleGenerator(Column column, Random random);

    <T> DataGenerator<T> buildDecimalGenerator(Column column, Random random);

    <T> DataGenerator<T> buildCharGenerator(Column column, Random random);

    <T> DataGenerator<T> buildNCharGenerator(Column column, Random random);

    <T> DataGenerator<T> buildVarcharGenerator(Column column, Random random);

    <T> DataGenerator<T> buildNVarcharGenerator(Column column, Random random);

    <T> DataGenerator<T> buildLongVarcharGenerator(Column column, Random random);

    <T> DataGenerator<T> buildLongNVarcharGenerator(Column column, Random random);

    <T> DataGenerator<T> buildDateGenerator(Column column, Random random);

    <T> DataGenerator<T> buildTimeGenerator(Column column, Random random);

    <T> DataGenerator<T> buildTimeWithTimezoneGenerator(Column column, Random random);

    <T> DataGenerator<T> buildTimestampGenerator(Column column, Random random);

    <T> DataGenerator<T> buildTimestampWithTimezoneGenerator(Column column, Random random);

    <T> DataGenerator<T> buildBinaryGenerator(Column column, Random random);

    <T> DataGenerator<T> buildVarbinaryGenerator(Column column, Random random);

    <T> DataGenerator<T> buildLongVarbinaryGenerator(Column column, Random random);

    <T> DataGenerator<T> buildBlobGenerator(Column column, Random random);

    <T> DataGenerator<T> buildClobGenerator(Column column, Random random);

    <T> DataGenerator<T> buildNClobGenerator(Column column, Random random);

    <T> DataGenerator<T> buildStructGenerator(Column column, Random random);

    <T> DataGenerator<T> buildArrayGenerator(Column column, Random random);

    <T> DataGenerator<T> buildBitGenerator(Column column, Random random);

    <T> DataGenerator<T> buildBooleanGenerator(Column column, Random random);

    <T> DataGenerator<T> buildOtherGenerator(Column column, Random random);

    <T> DataGenerator<T> buildNumericGenerator(Column column, Random random);

    <T> DataGenerator<T> buildJavaObjectGenerator(Column column, Random random);

    <T> DataGenerator<T> buildDistinctGenerator(Column column, Random random);

    <T> DataGenerator<T> buildNullGenerator(Column column, Random random);

    <T> DataGenerator<T> buildRefGenerator(Column column, Random random);

    <T> DataGenerator<T> buildDataLinkGenerator(Column column, Random random);

    <T> DataGenerator<T> buildRowIdGenerator(Column column, Random random);

    <T> DataGenerator<T> buildSqlXmlGenerator(Column column, Random random);

    <T> DataGenerator<T> buildRefCursorGenerator(Column column, Random random);


}
