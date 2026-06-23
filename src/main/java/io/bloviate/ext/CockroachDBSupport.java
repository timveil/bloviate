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

import io.bloviate.gen.*;

import java.sql.JDBCType;
import java.util.Map;

/**
 * CockroachDB-specific implementation of database support.
 * Provides CockroachDB-specific data generation and type mapping logic,
 * including support for CockroachDB's PostgreSQL-compatible types and
 * specialized data types like arrays, UUID, JSONB, and intervals.
 *
 * @since 1.0.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class CockroachDBSupport extends AbstractDatabaseSupport {

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {
        // CockroachDB BIT and BIT(n) are bit strings, not integers/booleans, so always
        // generate a bit string -- including the single-bit case (the generic default
        // would emit an integer for BIT(1), which CockroachDB rejects).
        registry.put(JDBCType.BIT, (column, random) ->
                new BitStringGenerator.Builder(random).size(column.maxSize()).build());

        // ARRAY and OTHER are dispatched on the driver-reported type name.
        registry.put(JDBCType.ARRAY, (column, random) -> {
            if ("_text".equalsIgnoreCase(column.typeName())) {
                return new StringArrayGenerator.Builder(random).build();
            } else if ("_int8".equalsIgnoreCase(column.typeName()) || "_int4".equalsIgnoreCase(column.typeName())) {
                return new IntegerArrayGenerator.Builder(random).build();
            }
            throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for ARRAY not supported");
        });

        registry.put(JDBCType.OTHER, (column, random) -> {
            if ("uuid".equalsIgnoreCase(column.typeName())) {
                return new UUIDGenerator.Builder(random).build();
            } else if ("varbit".equalsIgnoreCase(column.typeName())) {
                return new BitStringGenerator.Builder(random).size(column.maxSize()).build();
            } else if ("inet".equalsIgnoreCase(column.typeName())) {
                return new InetGenerator.Builder(random).build();
            } else if ("interval".equalsIgnoreCase(column.typeName())) {
                return new IntervalGenerator.Builder(random).build();
            } else if ("jsonb".equalsIgnoreCase(column.typeName())) {
                return new JsonbGenerator.Builder(random).build();
            }
            throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for OTHER not supported");
        });
    }
}
