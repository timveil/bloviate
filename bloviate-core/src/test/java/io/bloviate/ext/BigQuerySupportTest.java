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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bloviate.db.Column;
import io.bloviate.gen.BigDecimalGenerator;
import io.bloviate.gen.BooleanGenerator;
import io.bloviate.gen.ByteGenerator;
import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.DoubleGenerator;
import io.bloviate.gen.LongGenerator;
import io.bloviate.gen.SimpleStringGenerator;
import io.bloviate.gen.SqlDateGenerator;
import io.bloviate.gen.SqlTimeGenerator;
import io.bloviate.gen.SqlTimestampGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker-free coverage of {@link BigQuerySupport}. The BigQuery integration test needs a live
 * project, so these synthetic-{@link Column} tests are the only ones that run in CI — they carry
 * the type mapping, the size/precision clamps, and the rejection messages.
 */
class BigQuerySupportTest {

    /** How many draws to take before trusting a bound; generators are random per call. */
    private static final int DRAWS = 50;

    private static final Random RANDOM = new Random(1);

    private final BigQuerySupport support = new BigQuerySupport();

    private static Column column(JDBCType type, Integer maxSize, Integer maxDigits, String typeName) {
        return new Column("c", "t", null, null, type, maxSize, maxDigits, typeName, false, true, null, 1);
    }

    private DataGenerator<?> generatorFor(JDBCType type, Integer maxSize, Integer maxDigits, String typeName) {
        return support.getDataGenerator(column(type, maxSize, maxDigits, typeName), RANDOM);
    }

    private DataGenerator<?> generatorFor(JDBCType type, Integer maxSize, String typeName) {
        return generatorFor(type, maxSize, null, typeName);
    }

    private UnsupportedOperationException rejectionFor(JDBCType type, String typeName) {
        return assertThrows(UnsupportedOperationException.class, () -> generatorFor(type, null, typeName));
    }

    // ---------------------------------------------------------------- natively bindable types

    @Test
    void mapsNativelyBindableTypesToTheInheritedDefaults() {
        // these ride on AbstractDatabaseSupport's defaults; asserting them here means a future
        // change to registerDefaults that breaks BigQuery fails this test rather than a live fill
        assertInstanceOf(LongGenerator.class, generatorFor(JDBCType.BIGINT, null, "INT64"));
        assertInstanceOf(DoubleGenerator.class, generatorFor(JDBCType.DOUBLE, null, "FLOAT64"));
        assertInstanceOf(BooleanGenerator.class, generatorFor(JDBCType.BOOLEAN, null, "BOOL"));
        assertInstanceOf(SqlDateGenerator.class, generatorFor(JDBCType.DATE, null, "DATE"));
        assertInstanceOf(SqlTimeGenerator.class, generatorFor(JDBCType.TIME, null, "TIME"));
        assertInstanceOf(SqlTimestampGenerator.class, generatorFor(JDBCType.TIMESTAMP, null, "TIMESTAMP"));
    }

    // ---------------------------------------------------------------------------- STRING/BYTES

    @Test
    void honorsADeclaredStringWidth() {
        DataGenerator<?> generator = generatorFor(JDBCType.VARCHAR, 20, "STRING(20)");
        assertInstanceOf(SimpleStringGenerator.class, generator);

        for (int i = 0; i < DRAWS; i++) {
            assertTrue(((String) generator.generate()).length() <= 20);
        }
    }

    @Test
    void clampsABareStringDownFromTheBigQueryTypeMaximum() {
        // a bare STRING column reports 2,097,152 — the type limit, not a declared width
        DataGenerator<?> generator = generatorFor(JDBCType.VARCHAR, 2_097_152, "STRING");

        for (int i = 0; i < DRAWS; i++) {
            assertTrue(((String) generator.generate()).length() <= BigQuerySupport.MAX_STRING_LENGTH);
        }
    }

    @Test
    void clampsBytesDownFromTheBigQueryTypeMaximum() {
        DataGenerator<?> generator = generatorFor(JDBCType.VARBINARY, 10_485_760, "BYTES");
        assertInstanceOf(ByteGenerator.class, generator);

        for (int i = 0; i < DRAWS; i++) {
            assertTrue(((Byte[]) generator.generate()).length <= BigQuerySupport.MAX_BYTES_LENGTH);
        }
    }

    @Test
    void honorsADeclaredBytesWidth() {
        DataGenerator<?> generator = generatorFor(JDBCType.VARBINARY, 16, "BYTES(16)");

        for (int i = 0; i < DRAWS; i++) {
            assertTrue(((Byte[]) generator.generate()).length <= 16);
        }
    }

    @Test
    void fallsBackToTheCapWhenSizeIsUnreported() {
        // COLUMN_SIZE is nullable metadata; neither branch may unbox it
        DataGenerator<?> string = generatorFor(JDBCType.VARCHAR, null, "STRING");
        DataGenerator<?> bytes = generatorFor(JDBCType.VARBINARY, null, "BYTES");

        assertTrue(((String) string.generate()).length() <= BigQuerySupport.MAX_STRING_LENGTH);
        assertTrue(((Byte[]) bytes.generate()).length <= BigQuerySupport.MAX_BYTES_LENGTH);
    }

    // -------------------------------------------------------------------- NUMERIC / BIGNUMERIC

    @Test
    void generatesAValidNumeric() {
        DataGenerator<?> generator = generatorFor(JDBCType.NUMERIC, 38, 9, "NUMERIC");
        assertInstanceOf(BigDecimalGenerator.class, generator);

        for (int i = 0; i < DRAWS; i++) {
            BigDecimal value = (BigDecimal) generator.generate();
            assertTrue(value.scale() <= BigQuerySupport.MAX_NUMERIC_SCALE);
            assertTrue(value.precision() <= BigQuerySupport.MAX_NUMERIC_PRECISION);
        }
    }

    @Test
    void clampsBigNumericIntoNumericRange() {
        // BIGNUMERIC reports (76, 38). The driver binds every BigDecimal as NUMERIC, whose maximum
        // scale is 9, so an unclamped draw carries 25 fractional digits and BigQuery rejects it as
        // an out-of-range NUMERIC parameter — regardless of what the destination column could hold.
        DataGenerator<?> generator = generatorFor(JDBCType.NUMERIC, 76, 38, "BIGNUMERIC");

        for (int i = 0; i < DRAWS; i++) {
            BigDecimal value = (BigDecimal) generator.generate();
            assertTrue(value.scale() <= BigQuerySupport.MAX_NUMERIC_SCALE,
                    "scale was " + value.scale() + " for " + value);
            assertTrue(value.precision() <= BigQuerySupport.MAX_NUMERIC_PRECISION,
                    "precision was " + value.precision() + " for " + value);
        }
    }

    @Test
    void honorsADeclaredNumericScale() {
        DataGenerator<?> generator = generatorFor(JDBCType.NUMERIC, 10, 2, "NUMERIC(10, 2)");

        for (int i = 0; i < DRAWS; i++) {
            BigDecimal value = (BigDecimal) generator.generate();
            assertTrue(value.scale() <= 2, "scale was " + value.scale() + " for " + value);
            assertTrue(value.precision() <= 10, "precision was " + value.precision() + " for " + value);
        }
    }

    @Test
    void toleratesUnreportedNumericPrecisionAndScale() {
        DataGenerator<?> generator = generatorFor(JDBCType.NUMERIC, null, null, "NUMERIC");
        BigDecimal value = (BigDecimal) generator.generate();

        assertEquals(0, value.scale());
        assertTrue(value.precision() <= BigQuerySupport.MAX_NUMERIC_PRECISION);
    }

    // ------------------------------------------------------------------------ rejected types

    @Test
    void rejectsTypesWithNoParameterBinding() {
        Map<JDBCType, String> rejected = new LinkedHashMap<>();
        rejected.put(JDBCType.OTHER, "RANGE<DATE>");
        rejected.put(JDBCType.ARRAY, "ARRAY<INT64>");
        rejected.put(JDBCType.STRUCT, "STRUCT<a INT64, b STRING>");

        rejected.forEach(this::assertRejected);
    }

    // ------------------------------------------------------- server-constructed (Phase 2) types

    @Test
    void constructsJsonServerSide() {
        // the driver binds text as STRING and BigQuery will not coerce that into JSON, so the
        // value has to be built by the server
        DataGenerator<?> generator = generatorFor(JDBCType.VARCHAR, null, "JSON");

        assertEquals("PARSE_JSON(?)", generator.valueExpression());
        assertDoesNotThrow(() -> new ObjectMapper().readTree((String) generator.generate()));
    }

    @Test
    void constructsGeographyFromWellKnownText() {
        DataGenerator<?> generator = generatorFor(JDBCType.VARCHAR, null, "GEOGRAPHY");

        assertEquals("ST_GEOGFROMTEXT(?)", generator.valueExpression());

        for (int i = 0; i < DRAWS; i++) {
            String wkt = (String) generator.generate();
            assertTrue(wkt.startsWith("POINT(") && wkt.endsWith(")"), wkt);

            // WKT orders coordinates longitude-first, so the wider bound comes first
            String[] coordinates = wkt.substring(6, wkt.length() - 1).split(" ");
            assertEquals(2, coordinates.length, wkt);
            assertTrue(Math.abs(Double.parseDouble(coordinates[0])) <= 180, wkt);
            assertTrue(Math.abs(Double.parseDouble(coordinates[1])) <= 90, wkt);
        }
    }

    @Test
    void constructsIntervalServerSide() {
        DataGenerator<?> generator = generatorFor(JDBCType.VARCHAR, null, "INTERVAL");

        assertEquals("CAST(? AS INTERVAL)", generator.valueExpression());

        // BigQuery's interval literal is Y-M D H:M:S, which IntervalGenerator already emits.
        // Drawn once and reused, so a failure reports the value that actually failed rather than
        // a fresh (possibly valid) one.
        String interval = (String) generator.generate();
        assertTrue(interval.matches("-?\\d+-\\d+ -?\\d+ \\d+:\\d+:\\d+"), interval);
    }

    @Test
    void constructsDatetimeFromTheTimestampGenerator() {
        // DATETIME and TIMESTAMP are indistinguishable by JDBC type, so the type name decides;
        // both draw the same instant and only DATETIME is cast server-side
        DataGenerator<?> datetime = generatorFor(JDBCType.TIMESTAMP, null, "DATETIME");
        DataGenerator<?> timestamp = generatorFor(JDBCType.TIMESTAMP, null, "TIMESTAMP");

        assertEquals("CAST(? AS DATETIME)", datetime.valueExpression());
        assertEquals("?", timestamp.valueExpression());
        assertInstanceOf(Timestamp.class, datetime.generate());
    }

    @Test
    void leavesEveryOtherTypeBoundDirectly() {
        // a wrapped tuple costs the driver's load-job path, so nothing should be wrapped that does
        // not have to be
        assertEquals("?", generatorFor(JDBCType.VARCHAR, 20, "STRING(20)").valueExpression());
        assertEquals("?", generatorFor(JDBCType.VARBINARY, null, "BYTES").valueExpression());
        assertEquals("?", generatorFor(JDBCType.NUMERIC, 38, 9, "NUMERIC").valueExpression());
        assertEquals("?", generatorFor(JDBCType.BIGINT, null, "INT64").valueExpression());
        assertEquals("?", generatorFor(JDBCType.DATE, null, "DATE").valueExpression());
    }

    private void assertRejected(JDBCType jdbcType, String typeName) {
        UnsupportedOperationException e = rejectionFor(jdbcType, typeName);

        // the raw type name is echoed so the message names the column's actual declared type
        assertTrue(e.getMessage().contains(typeName), e.getMessage());
        // and points at the escape hatch, not at registerTypeName — which cannot match a
        // parameterized BigQuery type name like "numeric(10, 2)"
        assertTrue(e.getMessage().contains("ColumnConfiguration"), e.getMessage());
        assertFalse(e.getMessage().contains("registerTypeName"), e.getMessage());
    }

    @Test
    void rejectsStructRatherThanInheritingTheStructGenerator() {
        // AbstractDatabaseSupport maps STRUCT to SqlStructGenerator by default, which cannot know a
        // BigQuery struct's field shape and would silently generate garbage
        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(JDBCType.STRUCT, null, "STRUCT<a INT64>"));
    }

    // -------------------------------------------------------------------- type-name normalizer

    @Test
    void normalizesTypeNameCaseWhitespaceAndParameters() {
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(JDBCType.VARCHAR, 20, "string(20)"));
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(JDBCType.VARCHAR, null, "  STRING  "));
        assertInstanceOf(SimpleStringGenerator.class, generatorFor(JDBCType.VARCHAR, null, "String"));
    }

    @Test
    void normalizesTypeArgumentsWhenRejecting() {
        assertThrows(UnsupportedOperationException.class,
                () -> generatorFor(JDBCType.STRUCT, null, "Struct<a INT64>"));
    }

    @Test
    void toleratesAnUnreportedTypeName() {
        // TYPE_NAME should always be present, but a null must surface as the normal rejection
        // rather than a NullPointerException from the normalizer
        assertThrows(UnsupportedOperationException.class, () -> generatorFor(JDBCType.VARCHAR, 10, null));
        assertThrows(UnsupportedOperationException.class, () -> generatorFor(JDBCType.VARBINARY, 10, ""));
    }

    // ------------------------------------------------------------------------------ capabilities

    @Test
    void enablesBulkLoadWithNoOpConstraintHandling() throws Exception {
        assertTrue(support.supportsBulkLoad());

        // BigQuery keys are always NOT ENFORCED, so there is nothing to disable — and nothing that
        // needs a connection. Passing null proves the implementation never touches one.
        BulkLoadHandle handle = support.disableConstraints(null, null);
        assertTrue(handle.description().contains("NOT ENFORCED"));
        support.enableConstraints(null, null, handle);
    }

    @Test
    void prefersTheConnectionsOwnCommitBehavior() {
        assertTrue(support.prefersConnectionDefaultCommit());
    }

    @Test
    void recommendsNoBatchRewriteParameter() {
        // batch rewrite is unconditional in tbc-bq-jdbc, so there is no URL parameter to suggest
        assertNull(support.batchRewriteUrlParameter());
    }

    @Test
    void readsNoConstraints() {
        // BigQuery has no CHECK constraints or enum/domain types
        assertEquals(Map.of(), support.readConstraints(null, null, null));
    }
}
