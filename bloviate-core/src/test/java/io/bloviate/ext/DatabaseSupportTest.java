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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void configureRunsAfterSubclassConstruction() {
        // the registry is built lazily on first use, so a subclass's own fields are initialized
        // by the time configure() runs — an eager constructor call would see them as null
        DatabaseSupport support = new DefaultSupport() {
            private final GeneratorFactory custom = (column, random) -> new BooleanGenerator.Builder(random).build();

            @Override
            protected void configure(java.util.Map<JDBCType, GeneratorFactory> registry) {
                registry.put(JDBCType.INTEGER, custom);
            }
        };

        assertInstanceOf(BooleanGenerator.class, generatorFor(support, JDBCType.INTEGER, 10, "int4"));
    }

    @Test
    void defaultSupportToleratesNullColumnSize() {
        DatabaseSupport support = new DefaultSupport();

        // COLUMN_SIZE is nullable metadata; the defaults must not unbox it
        assertInstanceOf(BitGenerator.class, generatorFor(support, JDBCType.BIT, null, "bit"));
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(support, JDBCType.VARCHAR, null, "varchar"));
        assertInstanceOf(ByteGenerator.class, generatorFor(support, JDBCType.VARBINARY, null, "varbinary"));
    }

    @Test
    void h2ToleratesNullColumnSizeForBinary() {
        DatabaseSupport support = new H2Support();

        assertInstanceOf(ByteGenerator.class, generatorFor(support, JDBCType.BINARY, null, "binary"));
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

    // ---- integer widths and signedness (issue #641) ----------------------------------------

    @Test
    void tinyIntSignednessComesFromTheTypeName() {
        // JDBC's TINYINT is signed; MySQL and MariaDB mark an unsigned column in the type name,
        // and the two drivers spell it differently
        assertWholeNumberRange(new DefaultSupport(), JDBCType.TINYINT, 3, "TINYINT", -128, 127);
        assertWholeNumberRange(new H2Support(), JDBCType.TINYINT, 3, "TINYINT", -128, 127);
        assertWholeNumberRange(new MySQLSupport(), JDBCType.TINYINT, 3, "TINYINT UNSIGNED", 0, 255);
        assertWholeNumberRange(new MariaDBSupport(), JDBCType.TINYINT, 3, "tinyint(3) unsigned", 0, 255);
    }

    @Test
    void mySqlBitColumnsAreNumbersSizedToTheirWidth() {
        DatabaseSupport support = new MySQLSupport();

        // a MySQL BIT(n) holds an n-bit integer, not the '0'/'1' string the standard defines
        assertInstanceOf(LongGenerator.class, generatorFor(support, JDBCType.BIT, 8, "BIT"));
        assertWholeNumberRange(support, JDBCType.BIT, 3, "BIT", 0, 7);
        assertWholeNumberRange(support, JDBCType.BIT, 8, "BIT", 0, 255);
        assertWholeNumberRange(support, JDBCType.BIT, 17, "bit(17)", 0, 131_071);

        // the widest columns reach past Long.MAX_VALUE, so they draw from the widest range a long has
        assertWholeNumberRange(support, JDBCType.BIT, 64, "BIT", 0, Long.MAX_VALUE - 1);
        // a width the driver cannot have reported for a BIT column is clamped, never trusted upward
        assertWholeNumberRange(support, JDBCType.BIT, 9999, "BIT", 0, Long.MAX_VALUE - 1);
    }

    @Test
    void mySqlKeepsSingleBitValuesForBooleanColumns() {
        DatabaseSupport support = new MySQLSupport();

        assertInstanceOf(BitGenerator.class, generatorFor(support, JDBCType.BIT, 1, "BIT"));
        assertInstanceOf(BitGenerator.class, generatorFor(support, JDBCType.BIT, null, "BIT"));
        // with tinyInt1isBit on (the default) TINYINT(1) — and so BOOLEAN — also arrives as JDBC BIT.
        // Connector/J names it TINYINT, so it stays a single bit whatever size comes with it
        assertWholeNumberRange(support, JDBCType.BIT, 1, "TINYINT", 0, 1);
        assertWholeNumberRange(support, JDBCType.BIT, 3, "TINYINT", 0, 1);
    }

    /**
     * Draws enough values to assert both that none leaves {@code [min, max]} and that the generator
     * reaches into the upper half of it, which is what a range collapsed onto its bottom bit (or
     * onto the wrong sign) fails.
     */
    private static void assertWholeNumberRange(DatabaseSupport support, JDBCType type, Integer maxSize, String typeName,
                                               long min, long max) {
        DataGenerator<?> generator = support.getDataGenerator(column(type, maxSize, typeName), RANDOM);

        long midpoint = min + (max - min) / 2;
        boolean sawUpperHalf = false;

        for (int i = 0; i < 2_000; i++) {
            long value = ((Number) generator.generate()).longValue();
            assertTrue(value >= min && value <= max,
                    typeName + "(" + maxSize + ") produced " + value + ", outside [" + min + ", " + max + "]");
            sawUpperHalf |= value > midpoint;
        }

        assertTrue(sawUpperHalf || min == max,
                typeName + "(" + maxSize + ") never reached above " + midpoint + "; its range is not being used");
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
