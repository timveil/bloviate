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

package io.bloviate.util;

import io.bloviate.db.Column;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ColumnSeedTest {

    private static Column column(String name, String table, JDBCType type) {
        return new Column(name, table, "public", null, type, 32, null, type.getName(), false, true, null, 1);
    }

    @Test
    void seedIsStableForSameColumnAndBaseSeed() {
        Column column = column("c", "t", JDBCType.INTEGER);

        // a pure function of the column + base seed: identical inputs -> identical seed
        assertEquals(DatabaseUtils.columnSeed(column, 0L), DatabaseUtils.columnSeed(column, 0L));
        assertEquals(DatabaseUtils.columnSeed(column, 42L), DatabaseUtils.columnSeed(column, 42L));
    }

    @Test
    void seedDoesNotDependOnJdbcTypeEnumIdentity() {
        // JDBCType.hashCode() is identity-based (varies per JVM run); columnSeed must not use it,
        // so the value is fully determined by stable components. A hard-coded expectation here
        // would fail if the implementation regressed to Enum.hashCode().
        Column column = column("c", "t", JDBCType.INTEGER);

        long expected = 0L * 1_000_003L + java.util.Objects.hash("c", "t", "public", null, "INTEGER", 1);
        assertEquals(expected, DatabaseUtils.columnSeed(column, 0L));
    }

    @Test
    void differentBaseSeedsProduceDifferentSeeds() {
        Column column = column("c", "t", JDBCType.INTEGER);

        assertNotEquals(DatabaseUtils.columnSeed(column, 1L), DatabaseUtils.columnSeed(column, 2L));
    }

    @Test
    void differentColumnsProduceDifferentSeeds() {
        long a = DatabaseUtils.columnSeed(column("a", "t", JDBCType.INTEGER), 0L);
        long b = DatabaseUtils.columnSeed(column("b", "t", JDBCType.INTEGER), 0L);

        assertNotEquals(a, b);
    }
}
