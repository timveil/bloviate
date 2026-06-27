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

import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.SimpleStringGenerator;

import java.sql.JDBCType;
import java.util.Map;

/**
 * MySQL-specific {@link DatabaseSupport}.
 *
 * <p>MySQL's JDBC driver maps most types onto standard JDBC types that the cross-database
 * defaults already handle. The notable exception is {@code JSON}, which the driver reports
 * as {@link JDBCType#LONGVARCHAR} (type name {@code JSON}); a random string would fail the
 * server's JSON validation, so this support routes {@code JSON} columns to a JSON generator
 * while leaving ordinary text columns on the default string generator.
 *
 * <p>Some MySQL types remain unsupported because they need value-aware or binary generation
 * that standard JDBC metadata doesn't expose: {@code ENUM}/{@code SET} (must match the
 * declared member list), {@code GEOMETRY} (well-known binary), and {@code YEAR}.
 *
 * @since 1.0.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class MySQLSupport extends AbstractDatabaseSupport {

    @Override
    public String batchRewriteUrlParameter() {
        return "rewriteBatchedStatements";
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // MySQL JSON columns report as LONGVARCHAR with type name "JSON". Generate valid JSON
        // for those; everything else on this JDBC type stays an ordinary string.
        registry.put(JDBCType.LONGVARCHAR, (column, random) -> {
            if ("json".equalsIgnoreCase(column.typeName())) {
                return new JsonbGenerator.Builder(random).build();
            }
            Integer maxSize = column.maxSize();
            int size = (maxSize == null || maxSize <= 0) ? 2000 : maxSize;
            return new SimpleStringGenerator.Builder(random).size(size).build();
        });
    }
}
