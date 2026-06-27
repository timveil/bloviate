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
import io.bloviate.gen.BitStringGenerator;
import io.bloviate.gen.BooleanGenerator;
import io.bloviate.gen.CidrGenerator;
import io.bloviate.gen.InetGenerator;
import io.bloviate.gen.IntegerArrayGenerator;
import io.bloviate.gen.IntervalGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.MacAddressGenerator;
import io.bloviate.gen.StringArrayGenerator;
import io.bloviate.gen.UUIDGenerator;
import io.bloviate.gen.XmlGenerator;

import java.sql.JDBCType;
import java.util.Map;

/**
 * PostgreSQL-specific {@link DatabaseSupport}.
 *
 * <p>On top of the cross-database defaults, this registers generators for the PostgreSQL
 * types the JDBC driver surfaces as {@link JDBCType#OTHER}, {@link JDBCType#ARRAY},
 * {@link JDBCType#SQLXML}, and {@link JDBCType#BIT}, dispatching on the driver-reported
 * type name:
 *
 * <ul>
 *   <li>{@code uuid}, {@code json}, {@code jsonb}, {@code inet}, {@code cidr},
 *       {@code macaddr}, {@code macaddr8}, {@code interval}, {@code varbit} (all reported
 *       as {@code OTHER})</li>
 *   <li>{@code _text}, {@code _int2}, {@code _int4}, {@code _int8} arrays (reported as
 *       {@code ARRAY})</li>
 *   <li>{@code xml} (reported as {@code SQLXML})</li>
 *   <li>{@code bit}/{@code bit(n)} and {@code boolean} (both reported as {@code BIT},
 *       distinguished by type name)</li>
 * </ul>
 *
 * <p>Because CockroachDB speaks the PostgreSQL wire protocol through the same JDBC driver,
 * {@link CockroachDBSupport} extends this class and inherits all of the above.
 *
 * <p><strong>Connection requirement:</strong> these extension types are generated as their
 * text representations and bound as strings. PostgreSQL will not implicitly cast
 * {@code character varying} to {@code uuid}, {@code jsonb}, {@code inet}, {@code bit}, etc.,
 * so the JDBC connection must be opened with {@code stringtype=unspecified} (e.g.
 * {@code jdbc:postgresql://host/db?stringtype=unspecified}) for the server to infer each
 * column's type. Standard column types do not require this.
 *
 * @since 1.0.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class PostgresSupport extends AbstractDatabaseSupport {

    @Override
    public String batchRewriteUrlParameter() {
        return "reWriteBatchedInserts";
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // PostgreSQL reports both bit/bit(n) and boolean as JDBCType.BIT, distinguished by
        // type name. The generic default would emit an integer for a single bit, which the
        // server rejects for a bit column.
        registry.put(JDBCType.BIT, (column, random) -> {
            if ("bool".equalsIgnoreCase(column.typeName())) {
                return new BooleanGenerator.Builder(random).build();
            }
            return new BitStringGenerator.Builder(random).size(bitStringSize(column)).build();
        });

        // Arrays are dispatched on the driver-reported element type name.
        registry.put(JDBCType.ARRAY, (column, random) -> {
            String typeName = column.typeName();
            if ("_text".equalsIgnoreCase(typeName)) {
                return new StringArrayGenerator.Builder(random).build();
            } else if ("_int8".equalsIgnoreCase(typeName) || "_int4".equalsIgnoreCase(typeName) || "_int2".equalsIgnoreCase(typeName)) {
                return new IntegerArrayGenerator.Builder(random).build();
            }
            throw new UnsupportedOperationException("Data Type [" + typeName + "] for ARRAY not supported");
        });

        // xml surfaces as its own JDBC type.
        registry.put(JDBCType.SQLXML, (column, random) -> new XmlGenerator.Builder(random).build());

        // Most PostgreSQL extension types surface as OTHER, dispatched on the type name.
        registry.put(JDBCType.OTHER, (column, random) -> {
            String typeName = column.typeName() == null ? "" : column.typeName().toLowerCase();
            return switch (typeName) {
                case "uuid" -> new UUIDGenerator.Builder(random).build();
                case "json", "jsonb" -> new JsonbGenerator.Builder(random).build();
                case "inet" -> new InetGenerator.Builder(random).build();
                case "cidr" -> new CidrGenerator.Builder(random).build();
                case "macaddr" -> new MacAddressGenerator.Builder(random).build();
                case "macaddr8" -> new MacAddressGenerator.Builder(random).octets(8).build();
                case "interval" -> new IntervalGenerator.Builder(random).build();
                case "varbit" -> new BitStringGenerator.Builder(random).size(bitStringSize(column)).build();
                default -> throw new UnsupportedOperationException("Data Type [" + column.typeName() + "] for OTHER not supported");
            };
        });
    }

    /**
     * Bit-string length to generate, falling back to a small default when the column reports
     * no usable length (e.g. unbounded {@code bit varying}).
     */
    private static int bitStringSize(Column column) {
        Integer maxSize = column.maxSize();
        if (maxSize == null || maxSize <= 0 || maxSize > 256) {
            return 8;
        }
        return maxSize;
    }
}
