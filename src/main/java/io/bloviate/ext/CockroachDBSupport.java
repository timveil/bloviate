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

public class CockroachDBSupport extends AbstractDatabaseSupport {

    @Override
    public DataGenerator<?> buildArrayGenerator(Column column) {
        if ("_text".equalsIgnoreCase(column.typeName())) {
            return new StringArrayGenerator.Builder().build();
        } else if ("_int8".equalsIgnoreCase(column.typeName()) || "_int4".equalsIgnoreCase(column.typeName())) {
            return new IntegerArrayGenerator.Builder().build();
        } else {
            throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for ARRAY not supported");
        }
    }

    @Override
    public DataGenerator<?> buildOtherGenerator(Column column) {
        if ("uuid".equalsIgnoreCase(column.typeName())) {
            return new UUIDGenerator.Builder().build();
        } else if ("varbit".equalsIgnoreCase(column.typeName())) {
            return new BitStringGenerator.Builder().size(column.maxSize()).build();
        } else if ("inet".equalsIgnoreCase(column.typeName())) {
            return new InetGenerator.Builder().build();
        } else if ("interval".equalsIgnoreCase(column.typeName())) {
            return new IntervalGenerator.Builder().build();
        } else if ("jsonb".equalsIgnoreCase(column.typeName())) {
            return new JsonbGenerator.Builder().build();
        } else {
            throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for OTHER not supported");
        }
    }


}
