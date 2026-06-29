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

import io.bloviate.gen.ByteGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.ShortGenerator;
import io.bloviate.gen.UUIDGenerator;

import java.sql.JDBCType;
import java.util.Map;

/**
 * H2-specific {@link DatabaseSupport}.
 *
 * <p>H2 is an embedded, in-process database widely used as a JVM test fixture. It is largely
 * standard-SQL, so most columns map through the cross-database defaults in
 * {@link AbstractDatabaseSupport} (including {@code TIMESTAMP WITH TIME ZONE}, {@code CLOB},
 * and {@code BLOB}). This class customizes the few types where H2 diverges:
 *
 * <ul>
 *   <li>{@code TINYINT} — H2's is a <em>signed</em> 8-bit type ({@code -128..127}), whereas the
 *       cross-database default targets MySQL's unsigned {@code 0..255} range and would overflow.</li>
 *   <li>{@code UUID} — the H2 driver reports it as JDBC {@link JDBCType#BINARY} (16 bytes) with
 *       type name {@code "UUID"}; a real UUID is generated rather than 16 random bytes.</li>
 *   <li>{@code JSON} — surfaces as JDBC {@link JDBCType#OTHER} with type name {@code "JSON"}; H2
 *       parses the value, so valid JSON is generated.</li>
 * </ul>
 *
 * <p>H2's first-class {@code ARRAY} and {@code INTERVAL} types and its non-standard {@code ENUM}
 * and {@code GEOMETRY} types are not yet supported and cause
 * {@link #getDataGenerator} to throw.
 *
 * @since 2.18.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class H2Support extends AbstractDatabaseSupport {

    /** Creates the H2 support with its default configuration. */
    public H2Support() {
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // H2 TINYINT is signed (max 127); the cross-database default targets the unsigned 0..255
        // range (MySQL) and would overflow an H2 TINYINT column. Generate 0..127, which is valid
        // for the signed type (the generator requires a non-negative range).
        registry.put(JDBCType.TINYINT, (column, random) ->
                new ShortGenerator.Builder(random).start(0).end(127).build());

        // H2 reports UUID as JDBC BINARY (16 bytes), type name "UUID"; generate a real UUID for
        // those and leave ordinary binary columns on a byte generator.
        registry.put(JDBCType.BINARY, (column, random) -> {
            if ("uuid".equalsIgnoreCase(column.typeName())) {
                return new UUIDGenerator.Builder(random).build();
            }
            return new ByteGenerator.Builder(random).size(column.maxSize()).build();
        });

        // H2 JSON surfaces as OTHER (type name "JSON"); H2 parses the value, so generate valid JSON.
        registry.put(JDBCType.OTHER, (column, random) -> {
            if ("json".equalsIgnoreCase(column.typeName())) {
                return new JsonbGenerator.Builder(random).build();
            }
            throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for OTHER not supported");
        });
    }
}
