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
    void postgresStillMapsStandardTypes() {
        DatabaseSupport support = new PostgresSupport();

        assertInstanceOf(IntegerGenerator.class, generatorFor(support, JDBCType.INTEGER, 10, "int4"));
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(support, JDBCType.VARCHAR, 32, "varchar"));
        // PostgreSQL reports boolean as JDBCType.BIT with type name "bool"
        assertInstanceOf(BooleanGenerator.class, generatorFor(support, JDBCType.BIT, 1, "bool"));
    }

    @Test
    void postgresDispatchesBitVarbitAndArraysAndXml() {
        DatabaseSupport support = new PostgresSupport();

        assertInstanceOf(BitStringGenerator.class, generatorFor(support, JDBCType.BIT, 1, "bit"));
        assertInstanceOf(BitStringGenerator.class, generatorFor(support, JDBCType.OTHER, 3, "varbit"));
        assertInstanceOf(StringArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_text"));
        assertInstanceOf(IntegerArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_int8"));
        assertInstanceOf(IntegerArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_int4"));
        assertInstanceOf(XmlGenerator.class, generatorFor(support, JDBCType.SQLXML, null, "xml"));
    }

    @Test
    void postgresDispatchesOtherByTypeName() {
        DatabaseSupport support = new PostgresSupport();

        assertInstanceOf(UUIDGenerator.class, generatorFor(support, JDBCType.OTHER, null, "uuid"));
        assertInstanceOf(JsonbGenerator.class, generatorFor(support, JDBCType.OTHER, null, "json"));
        assertInstanceOf(JsonbGenerator.class, generatorFor(support, JDBCType.OTHER, null, "jsonb"));
        assertInstanceOf(InetGenerator.class, generatorFor(support, JDBCType.OTHER, null, "inet"));
        assertInstanceOf(CidrGenerator.class, generatorFor(support, JDBCType.OTHER, null, "cidr"));
        assertInstanceOf(MacAddressGenerator.class, generatorFor(support, JDBCType.OTHER, null, "macaddr"));
        assertInstanceOf(MacAddressGenerator.class, generatorFor(support, JDBCType.OTHER, null, "macaddr8"));
        assertInstanceOf(IntervalGenerator.class, generatorFor(support, JDBCType.OTHER, null, "interval"));
    }

    @Test
    void postgresRejectsUnknownOtherAndArrayTypeNames() {
        DatabaseSupport support = new PostgresSupport();

        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(support, JDBCType.OTHER, null, "geometry"));
        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(support, JDBCType.ARRAY, null, "_point"));
    }

    @Test
    void cockroachInheritsPostgresTypeHandling() {
        DatabaseSupport support = new CockroachDBSupport();

        // CockroachDBSupport extends PostgresSupport, so it resolves the same generators
        assertInstanceOf(BitStringGenerator.class, generatorFor(support, JDBCType.BIT, 1, "bit"));
        assertInstanceOf(StringArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_text"));
        assertInstanceOf(IntegerArrayGenerator.class, generatorFor(support, JDBCType.ARRAY, null, "_int8"));
        assertInstanceOf(UUIDGenerator.class, generatorFor(support, JDBCType.OTHER, null, "uuid"));
        assertInstanceOf(InetGenerator.class, generatorFor(support, JDBCType.OTHER, null, "inet"));
        assertInstanceOf(JsonbGenerator.class, generatorFor(support, JDBCType.OTHER, null, "jsonb"));
        assertInstanceOf(IntegerGenerator.class, generatorFor(support, JDBCType.INTEGER, 10, "int4"));
        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(support, JDBCType.OTHER, null, "geometry"));
    }

    @Test
    void mySqlMapsJsonButLeavesTextAsString() {
        DatabaseSupport support = new MySQLSupport();

        // JSON reports as LONGVARCHAR with type name "JSON"
        assertInstanceOf(JsonbGenerator.class, generatorFor(support, JDBCType.LONGVARCHAR, 1073741823, "JSON"));
        // ordinary text on the same JDBC type stays a string
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(support, JDBCType.LONGVARCHAR, 65535, "TEXT"));
        // standard types still behave like the default
        assertInstanceOf(IntegerGenerator.class, generatorFor(support, JDBCType.INTEGER, 10, "INT"));
    }
}
