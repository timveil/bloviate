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
import io.bloviate.gen.DataGenerator;

import java.util.Random;

public interface DatabaseSupport {

    DataGenerator<?> getDataGenerator(Column column, Random random);

    DataGenerator<?> buildTinyIntGenerator(Column column, Random random);

    DataGenerator<?> buildSmallIntGenerator(Column column, Random random);

    DataGenerator<?> buildIntegerGenerator(Column column, Random random);

    DataGenerator<?> buildBigIntGenerator(Column column, Random random);

    DataGenerator<?> buildFloatGenerator(Column column, Random random);

    DataGenerator<?> buildRealGenerator(Column column, Random random);

    DataGenerator<?> buildDoubleGenerator(Column column, Random random);

    DataGenerator<?> buildDecimalGenerator(Column column, Random random);

    DataGenerator<?> buildCharGenerator(Column column, Random random);

    DataGenerator<?> buildNCharGenerator(Column column, Random random);

    DataGenerator<?> buildVarcharGenerator(Column column, Random random);

    DataGenerator<?> buildNVarcharGenerator(Column column, Random random);

    DataGenerator<?> buildLongVarcharGenerator(Column column, Random random);

    DataGenerator<?> buildLongNVarcharGenerator(Column column, Random random);

    DataGenerator<?> buildDateGenerator(Column column, Random random);

    DataGenerator<?> buildTimeGenerator(Column column, Random random);

    DataGenerator<?> buildTimeWithTimezoneGenerator(Column column, Random random);

    DataGenerator<?> buildTimestampGenerator(Column column, Random random);

    DataGenerator<?> buildTimestampWithTimezoneGenerator(Column column, Random random);

    DataGenerator<?> buildBinaryGenerator(Column column, Random random);

    DataGenerator<?> buildVarbinaryGenerator(Column column, Random random);

    DataGenerator<?> buildLongVarbinaryGenerator(Column column, Random random);

    DataGenerator<?> buildBlobGenerator(Column column, Random random);

    DataGenerator<?> buildClobGenerator(Column column, Random random);

    DataGenerator<?> buildNClobGenerator(Column column, Random random);

    DataGenerator<?> buildStructGenerator(Column column, Random random);

    DataGenerator<?> buildArrayGenerator(Column column, Random random);

    DataGenerator<?> buildBitGenerator(Column column, Random random);

    DataGenerator<?> buildBooleanGenerator(Column column, Random random);

    DataGenerator<?> buildOtherGenerator(Column column, Random random);

    DataGenerator<?> buildNumericGenerator(Column column, Random random);

    DataGenerator<?> buildJavaObjectGenerator(Column column, Random random);

    DataGenerator<?> buildDistinctGenerator(Column column, Random random);

    DataGenerator<?> buildNullGenerator(Column column, Random random);

    DataGenerator<?> buildRefGenerator(Column column, Random random);

    DataGenerator<?> buildDataLinkGenerator(Column column, Random random);

    DataGenerator<?> buildRowIdGenerator(Column column, Random random);

    DataGenerator<?> buildSqlXmlGenerator(Column column, Random random);

    DataGenerator<?> buildRefCursorGenerator(Column column, Random random);


}
