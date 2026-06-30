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

package io.bloviate.bench;

import io.bloviate.db.Column;

import java.sql.JDBCType;
import java.util.List;

/**
 * Builds synthetic {@link Column} metadata for the JMH benchmarks so they can resolve real
 * generators via {@link io.bloviate.ext.DatabaseSupport#getDataGenerator} — the same path
 * {@link io.bloviate.db.TableFiller} uses — without standing up a database.
 */
final class BenchColumns {

    private BenchColumns() {
    }

    /**
     * Creates a {@link Column} carrying only the metadata the generators actually read
     * ({@code maxSize}/{@code maxDigits} for sized types, {@code typeName} for the PostgreSQL
     * extension types dispatched as {@code OTHER}).
     */
    static Column column(String name, JDBCType jdbcType, String typeName, Integer maxSize, Integer maxDigits) {
        return new Column(name, "bench", null, null, jdbcType, maxSize, maxDigits, typeName, false, false, null, 1);
    }

    /**
     * A representative "wide" row mixing common standard types with a couple of PostgreSQL
     * extension types — the shape {@link RowDispatchBenchmark} iterates to measure per-cell
     * generator dispatch.
     */
    static List<Column> wideRow() {
        return List.of(
                column("id", JDBCType.BIGINT, "int8", null, null),
                column("code", JDBCType.INTEGER, "int4", null, null),
                column("ref_id", JDBCType.BIGINT, "int8", null, null),
                column("name", JDBCType.VARCHAR, "varchar", 64, null),
                column("description", JDBCType.VARCHAR, "varchar", 256, null),
                column("status", JDBCType.VARCHAR, "varchar", 16, null),
                column("price", JDBCType.NUMERIC, "numeric", 12, 2),
                column("score", JDBCType.DOUBLE, "float8", null, null),
                column("ratio", JDBCType.REAL, "float4", null, null),
                column("quantity", JDBCType.INTEGER, "int4", null, null),
                column("active", JDBCType.BOOLEAN, "bool", null, null),
                column("created", JDBCType.TIMESTAMP, "timestamp", null, null),
                column("updated", JDBCType.TIMESTAMP, "timestamp", null, null),
                column("flags", JDBCType.BIT, "bit", 8, null),
                column("external_id", JDBCType.OTHER, "uuid", null, null),
                column("payload", JDBCType.OTHER, "jsonb", null, null));
    }

    /**
     * A portable row of standard SQL types only — the ones every {@link io.bloviate.ext.DatabaseSupport}
     * resolves through the shared cross-database defaults (no PostgreSQL extension types). Used by the
     * cross-support resolution benchmark (so every support can resolve every column) and by the bind
     * benchmark (so a plain {@code CREATE TABLE} of these types accepts the generated values). The
     * column types are mirrored by {@code BindBenchmark}'s DDL, in this order.
     */
    static List<Column> standardRow() {
        return List.of(
                column("c_int", JDBCType.INTEGER, "int", null, null),
                column("c_bigint", JDBCType.BIGINT, "bigint", null, null),
                column("c_double", JDBCType.DOUBLE, "double", null, null),
                column("c_numeric", JDBCType.NUMERIC, "numeric", 12, 2),
                column("c_varchar", JDBCType.VARCHAR, "varchar", 64, null),
                column("c_bool", JDBCType.BOOLEAN, "boolean", null, null),
                column("c_date", JDBCType.DATE, "date", null, null),
                column("c_ts", JDBCType.TIMESTAMP, "timestamp", null, null));
    }
}
