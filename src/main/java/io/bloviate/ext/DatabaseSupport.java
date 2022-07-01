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

public interface DatabaseSupport {

    DataGenerator<?> getDataGenerator(Column column);

    DataGenerator<?> buildTinyIntGenerator(Column column);

    DataGenerator<?> buildSmallIntGenerator(Column column);

    DataGenerator<?> buildIntegerGenerator(Column column);

    DataGenerator<?> buildBigIntGenerator(Column column);

    DataGenerator<?> buildFloatGenerator(Column column);

    DataGenerator<?> buildRealGenerator(Column column);

    DataGenerator<?> buildDoubleGenerator(Column column);

    DataGenerator<?> buildDecimalGenerator(Column column);

    DataGenerator<?> buildCharGenerator(Column column);

    DataGenerator<?> buildNCharGenerator(Column column);

    DataGenerator<?> buildVarcharGenerator(Column column);

    DataGenerator<?> buildNVarcharGenerator(Column column);

    DataGenerator<?> buildLongVarcharGenerator(Column column);

    DataGenerator<?> buildLongNVarcharGenerator(Column column);

    DataGenerator<?> buildDateGenerator(Column column);

    DataGenerator<?> buildTimeGenerator(Column column);

    DataGenerator<?> buildTimeWithTimezoneGenerator(Column column);

    DataGenerator<?> buildTimestampGenerator(Column column);

    DataGenerator<?> buildTimestampWithTimezoneGenerator(Column column);

    DataGenerator<?> buildBinaryGenerator(Column column);

    DataGenerator<?> buildVarbinaryGenerator(Column column);

    DataGenerator<?> buildLongVarbinaryGenerator(Column column);

    DataGenerator<?> buildBlobGenerator(Column column);

    DataGenerator<?> buildClobGenerator(Column column);

    DataGenerator<?> buildNClobGenerator(Column column);

    DataGenerator<?> buildStructGenerator(Column column);

    DataGenerator<?> buildArrayGenerator(Column column);

    DataGenerator<?> buildBitGenerator(Column column);

    DataGenerator<?> buildBooleanGenerator(Column column);

    DataGenerator<?> buildOtherGenerator(Column column);

    DataGenerator<?> buildNumericGenerator(Column column);

    DataGenerator<?> buildJavaObjectGenerator(Column column);

    DataGenerator<?> buildDistinctGenerator(Column column);

    DataGenerator<?> buildNullGenerator(Column column);

    DataGenerator<?> buildRefGenerator(Column column);

    DataGenerator<?> buildDataLinkGenerator(Column column);

    DataGenerator<?> buildRowIdGenerator(Column column);

    DataGenerator<?> buildSqlXmlGenerator(Column column);

    DataGenerator<?> buildRefCursorGenerator(Column column);


}
