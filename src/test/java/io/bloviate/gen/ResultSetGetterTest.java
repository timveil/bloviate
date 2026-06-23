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

package io.bloviate.gen;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression tests for generators whose {@code get(ResultSet, int)} previously returned a
 * hard-coded {@code null} (see issue #422). Each generator should read the column and return a
 * value, mapping a SQL {@code NULL} column to {@code null}.
 */
class ResultSetGetterTest {

    private static final int COLUMN = 1;
    private static final Random RANDOM = new Random();

    @Test
    void instantGeneratorReadsTimestamp() throws SQLException {
        InstantGenerator generator = new InstantGenerator.Builder(RANDOM).build();
        Instant expected = Instant.ofEpochMilli(1_600_000_000_000L);

        Instant value = generator.get(StubResultSet.returning("getTimestamp", Timestamp.from(expected)), COLUMN);

        assertEquals(expected, value);
    }

    @Test
    void instantGeneratorReturnsNullForSqlNull() throws SQLException {
        InstantGenerator generator = new InstantGenerator.Builder(RANDOM).build();

        assertNull(generator.get(StubResultSet.returning("getTimestamp", null), COLUMN));
    }

    @Test
    void dateGeneratorReadsTimestamp() throws SQLException {
        DateGenerator generator = new DateGenerator.Builder(RANDOM).build();
        long millis = 1_600_000_000_000L;

        Date value = generator.get(StubResultSet.returning("getTimestamp", new Timestamp(millis)), COLUMN);

        assertEquals(new Date(millis), value);
    }

    @Test
    void dateGeneratorReturnsNullForSqlNull() throws SQLException {
        DateGenerator generator = new DateGenerator.Builder(RANDOM).build();

        assertNull(generator.get(StubResultSet.returning("getTimestamp", null), COLUMN));
    }

    @Test
    void integerArrayGeneratorReadsArray() throws SQLException {
        IntegerArrayGenerator generator = new IntegerArrayGenerator.Builder(RANDOM).build();
        Object[] elements = {1, 2, 3};

        Integer[] value = generator.get(StubResultSet.returning("getArray", StubResultSet.array(elements)), COLUMN);

        assertArrayEquals(new Integer[]{1, 2, 3}, value);
    }

    @Test
    void integerArrayGeneratorReturnsNullForSqlNull() throws SQLException {
        IntegerArrayGenerator generator = new IntegerArrayGenerator.Builder(RANDOM).build();

        assertNull(generator.get(StubResultSet.returning("getArray", null), COLUMN));
    }

    @Test
    void stringArrayGeneratorReadsArray() throws SQLException {
        StringArrayGenerator generator = new StringArrayGenerator.Builder(RANDOM).build();
        Object[] elements = {"alpha", "beta"};

        String[] value = generator.get(StubResultSet.returning("getArray", StubResultSet.array(elements)), COLUMN);

        assertArrayEquals(new String[]{"alpha", "beta"}, value);
    }

    @Test
    void stringArrayGeneratorReturnsNullForSqlNull() throws SQLException {
        StringArrayGenerator generator = new StringArrayGenerator.Builder(RANDOM).build();

        assertNull(generator.get(StubResultSet.returning("getArray", null), COLUMN));
    }

    @Test
    void jsonbGeneratorReadsString() throws SQLException {
        JsonbGenerator generator = new JsonbGenerator.Builder(RANDOM).build();
        String json = "{\"key\":\"value\"}";

        assertEquals(json, generator.get(StubResultSet.returning("getString", json), COLUMN));
    }

    @Test
    void jsonbGeneratorReturnsNullForSqlNull() throws SQLException {
        JsonbGenerator generator = new JsonbGenerator.Builder(RANDOM).build();

        assertNull(generator.get(StubResultSet.returning("getString", null), COLUMN));
    }

    @Test
    void sqlStructGeneratorReadsObject() throws SQLException {
        SqlStructGenerator generator = new SqlStructGenerator.Builder(RANDOM).build();
        Struct struct = stubStruct();

        assertSame(struct, generator.get(StubResultSet.returning("getObject", struct), COLUMN));
    }

    @Test
    void sqlStructGeneratorReturnsNullForSqlNull() throws SQLException {
        SqlStructGenerator generator = new SqlStructGenerator.Builder(RANDOM).build();

        assertNull(generator.get(StubResultSet.returning("getObject", null), COLUMN));
    }

    private static Struct stubStruct() {
        return (Struct) Proxy.newProxyInstance(
                ResultSetGetterTest.class.getClassLoader(),
                new Class<?>[]{Struct.class},
                (proxy, method, args) -> null);
    }
}
