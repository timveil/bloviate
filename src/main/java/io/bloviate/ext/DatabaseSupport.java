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
import io.bloviate.gen.DataGenerator;

import java.math.BigDecimal;
import java.sql.*;

public interface DatabaseSupport {

    DataGenerator<Object> getDataGenerator(Column column);

    DataGenerator<Short> buildTinyIntGenerator(Column column);

    DataGenerator<Short> buildSmallIntGenerator(Column column);

    DataGenerator<Integer> buildIntegerGenerator(Column column);

    DataGenerator<Long> buildBigIntGenerator(Column column);

    DataGenerator<Float> buildFloatGenerator(Column column);

    DataGenerator<Float> buildRealGenerator(Column column);

    DataGenerator<Double> buildDoubleGenerator(Column column);

    DataGenerator<BigDecimal> buildDecimalGenerator(Column column);

    DataGenerator<String> buildCharGenerator(Column column);

    DataGenerator<String> buildNCharGenerator(Column column);

    DataGenerator<String> buildVarcharGenerator(Column column);

    DataGenerator<String> buildNVarcharGenerator(Column column);

    DataGenerator<String> buildLongVarcharGenerator(Column column);

    DataGenerator<String> buildLongNVarcharGenerator(Column column);

    DataGenerator<Date> buildDateGenerator(Column column);

    DataGenerator<Time> buildTimeGenerator(Column column);

    DataGenerator<Time> buildTimeWithTimezoneGenerator(Column column);

    DataGenerator<Timestamp> buildTimestampGenerator(Column column);

    DataGenerator<Timestamp> buildTimestampWithTimezoneGenerator(Column column);

    DataGenerator<Byte[]> buildBinaryGenerator(Column column);

    DataGenerator<Byte[]> buildVarbinaryGenerator(Column column);

    DataGenerator<Byte[]> buildLongVarbinaryGenerator(Column column);

    DataGenerator<Blob> buildBlobGenerator(Column column);

    DataGenerator<Clob> buildClobGenerator(Column column);

    DataGenerator<Clob> buildNClobGenerator(Column column);

    DataGenerator<Struct> buildStructGenerator(Column column);

    DataGenerator<?> buildArrayGenerator(Column column);

    DataGenerator<?> buildBitGenerator(Column column);

    DataGenerator<Boolean> buildBooleanGenerator(Column column);

    DataGenerator<Object> buildOtherGenerator(Column column);

    DataGenerator<BigDecimal> buildNumericGenerator(Column column);

    DataGenerator<Object> buildJavaObjectGenerator(Column column);

    DataGenerator<Object> buildDistinctGenerator(Column column);

    DataGenerator<Object> buildNullGenerator(Column column);

    DataGenerator<Object> buildRefGenerator(Column column);

    DataGenerator<Object> buildDataLinkGenerator(Column column);

    DataGenerator<Object> buildRowIdGenerator(Column column);

    DataGenerator<Object> buildSqlXmlGenerator(Column column);

    DataGenerator<Object> buildRefCursorGenerator(Column column);


}
