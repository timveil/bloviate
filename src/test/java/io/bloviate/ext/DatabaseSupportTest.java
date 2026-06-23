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
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseSupportTest {

    private static final Random RANDOM = new Random(1);

    private static Column column(JDBCType type, Integer maxSize, String typeName) {
        return new Column("c", "t", null, null, type, maxSize, null, typeName, false, true, null, 1);
    }

    private static Object generatorFor(DatabaseSupport support, JDBCType type, Integer maxSize, String typeName) {
        return support.getDataGenerator(column(type, maxSize, typeName), RANDOM);
    }

    @Test
    void defaultSupportMapsCommonTypes() {
        DatabaseSupport support = new DefaultSupport();

        assertInstanceOf(IntegerGenerator.class, generatorFor(support, JDBCType.INTEGER, 10, "int4"));
        assertInstanceOf(LongGenerator.class, generatorFor(support, JDBCType.BIGINT, 19, "int8"));
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(support, JDBCType.VARCHAR, 32, "varchar"));
        assertInstanceOf(BooleanGenerator.class, generatorFor(support, JDBCType.BOOLEAN, null, "bool"));
        assertInstanceOf(BigDecimalGenerator.class, generatorFor(support, JDBCType.NUMERIC, 10, "numeric"));
    }

    @Test
    void defaultSupportDistinguishesSingleBitFromBitString() {
        DatabaseSupport support = new DefaultSupport();

        assertInstanceOf(BitGenerator.class, generatorFor(support, JDBCType.BIT, 1, "bit"));
        assertInstanceOf(BitStringGenerator.class, generatorFor(support, JDBCType.BIT, 8, "bit"));
    }

    @Test
    void defaultSupportThrowsForUnregisteredType() {
        DatabaseSupport support = new DefaultSupport();

        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(support, JDBCType.ARRAY, null, "_int4"));
    }

    @Test
    void postgresAndMySqlBehaveLikeDefault() {
        assertInstanceOf(IntegerGenerator.class, generatorFor(new PostgresSupport(), JDBCType.INTEGER, 10, "int4"));
        assertInstanceOf(IntegerGenerator.class, generatorFor(new MySQLSupport(), JDBCType.INTEGER, 10, "int4"));
    }

    @Test
    void cockroachOverridesBitToBitString() {
        DatabaseSupport support = new CockroachDBSupport();

        // CRDB treats BIT(1) as a bit string, unlike the default which would use BitGenerator
        assertInstanceOf(BitStringGenerator.class, generatorFor(support, JDBCType.BIT, 1, "bit"));
    }

    @Test
    void cockroachDispatchesArrayAndOtherByTypeName() {
        DatabaseSupport support = new CockroachDBSupport();

        assertInstanceOf(StringArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_text"));
        assertInstanceOf(IntegerArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_int8"));
        assertInstanceOf(UUIDGenerator.class, generatorFor(support, JDBCType.OTHER, null, "uuid"));
        assertInstanceOf(InetGenerator.class, generatorFor(support, JDBCType.OTHER, null, "inet"));
        assertInstanceOf(JsonbGenerator.class, generatorFor(support, JDBCType.OTHER, null, "jsonb"));
    }

    @Test
    void cockroachStillProvidesDefaultsAndRejectsUnknownTypeNames() {
        DatabaseSupport support = new CockroachDBSupport();

        // inherited default still works
        assertInstanceOf(IntegerGenerator.class, generatorFor(support, JDBCType.INTEGER, 10, "int4"));
        // unknown ARRAY/OTHER type names are rejected
        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(support, JDBCType.OTHER, null, "geometry"));
    }
}
