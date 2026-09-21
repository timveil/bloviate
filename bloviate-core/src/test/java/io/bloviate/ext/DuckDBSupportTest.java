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
import io.bloviate.gen.ByteGenerator;
import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.ShortGenerator;
import io.bloviate.gen.UUIDGenerator;
import io.bloviate.gen.WeightedCategoricalGenerator;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DuckDBSupport}'s type dispatch (issue #451). {@code DuckDBFillerTest} drives
 * the same types through a real DuckDB; these pin the decisions that are easy to get silently wrong,
 * and the ones that must fail loudly rather than generate a value the insert would reject.
 *
 * <p>The type names here are the ones the driver actually reports, taken from a metadata dump of a
 * table declaring every DuckDB type.
 */
class DuckDBSupportTest {

    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();

    private static final DuckDBSupport SUPPORT = new DuckDBSupport();

    private static Column column(JDBCType jdbcType, String typeName) {
        return new Column("c", "t", null, null, jdbcType, null, null, typeName, false, false, null, 1);
    }

    private static DataGenerator<?> generator(JDBCType jdbcType, String typeName) {
        return SUPPORT.getDataGenerator(column(jdbcType, typeName), RANDOM);
    }

    /**
     * The one that would otherwise go wrong quietly: DuckDB reports an unsigned integer as the next
     * signed JDBC type up, so the default would generate negatives for it.
     */
    @Test
    void unsignedIntegersAreRangedFromZero() {
        for (int i = 0; i < 200; i++) {
            assertTrue(((Number) generator(JDBCType.SMALLINT, "UTINYINT").generate()).longValue() >= 0);
            assertTrue(((Number) generator(JDBCType.INTEGER, "USMALLINT").generate()).longValue() >= 0);
            assertTrue(((Number) generator(JDBCType.BIGINT, "UINTEGER").generate()).longValue() >= 0);
            assertTrue(((Number) generator(JDBCType.OTHER, "UBIGINT").generate()).longValue() >= 0);
            assertTrue(((Number) generator(JDBCType.OTHER, "UHUGEINT").generate()).longValue() >= 0);
        }
    }

    /** An unsigned column's values must also stay inside its own width, not just above zero. */
    @Test
    void unsignedIntegersStayInsideTheirWidth() {
        Set<Long> tiny = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            long value = ((Number) generator(JDBCType.SMALLINT, "UTINYINT").generate()).longValue();
            assertTrue(value < 256, "UTINYINT out of range: " + value);
            tiny.add(value);
        }
        assertTrue(tiny.size() > 1, "the generator must vary");
    }

    /**
     * A genuinely signed column of the same JDBC type keeps the cross-database default, rather than
     * being narrowed to the unsigned range that shares its JDBC type.
     */
    @Test
    void signedIntegersKeepTheDefaultRange() {
        DataGenerator<?> generator = generator(JDBCType.SMALLINT, "SMALLINT");
        assertInstanceOf(ShortGenerator.class, generator);

        boolean beyondUnsignedByte = false;
        for (int i = 0; i < 500 && !beyondUnsignedByte; i++) {
            beyondUnsignedByte = ((Number) generator.generate()).longValue() > 255;
        }
        assertTrue(beyondUnsignedByte, "a signed SMALLINT must not be narrowed to the UTINYINT range");
    }

    @Test
    void vendorTypesBehindOtherResolveByTypeName() {
        assertInstanceOf(UUIDGenerator.class, generator(JDBCType.OTHER, "UUID"));
        assertInstanceOf(JsonbGenerator.class, generator(JDBCType.OTHER, "JSON"));
        assertInstanceOf(BitStringGenerator.class, generator(JDBCType.BIT, "BIT"));
        // the driver has no setBlob, so a BLOB is bound as bytes
        assertInstanceOf(ByteGenerator.class, generator(JDBCType.BLOB, "BLOB"));
    }

    /** {@code TYPE_NAME} carries the declared labels, so only those are generated. */
    @Test
    void anEnumGeneratesOneOfItsDeclaredLabels() {
        DataGenerator<?> generator = generator(JDBCType.OTHER, "ENUM('ok', 'sad', 'happy')");
        assertInstanceOf(WeightedCategoricalGenerator.class, generator);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add((String) generator.generate());
        }
        assertEquals(Set.of("ok", "sad", "happy"), seen);
    }

    /** The labels are case-sensitive values, so they must not be folded with the type name. */
    @Test
    void enumLabelsKeepTheirCase() {
        DataGenerator<?> generator = generator(JDBCType.OTHER, "ENUM('Ok', 'SAD')");

        for (int i = 0; i < 100; i++) {
            assertTrue(Set.of("Ok", "SAD").contains(generator.generate()), "labels must not be case-folded");
        }
    }

    /** DuckDB doubles a quote inside a label, as SQL does. */
    @Test
    void anEnumLabelMayContainAQuote() {
        DataGenerator<?> generator = generator(JDBCType.OTHER, "ENUM('it''s', 'fine')");

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add((String) generator.generate());
        }
        assertEquals(Set.of("it's", "fine"), seen);
    }

    /**
     * The composite and variable-width types have no portable JDBC parameter form, so they must fail
     * loudly rather than generate something the insert rejects.
     */
    @Test
    void unsupportedTypesThrowRatherThanGenerateInvalidData() {
        List<Column> unsupported = List.of(
                column(JDBCType.OTHER, "INTEGER[]"),
                column(JDBCType.OTHER, "INTEGER[3]"),
                column(JDBCType.OTHER, "MAP(VARCHAR, INTEGER)"),
                column(JDBCType.OTHER, "INTERVAL"),
                column(JDBCType.OTHER, "BIGNUM"),
                column(JDBCType.STRUCT, "STRUCT(a INTEGER, b VARCHAR)"));

        for (Column column : unsupported) {
            UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
                    () -> SUPPORT.getDataGenerator(column, RANDOM), column.typeName());
            assertTrue(thrown.getMessage().contains(column.typeName()), thrown.getMessage());
        }
    }
}
