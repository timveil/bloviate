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

package io.bloviate.db;

import io.bloviate.ext.CockroachDBSupport;
import io.bloviate.ext.DefaultSupport;
import io.bloviate.ext.MySQLSupport;
import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkLoadStrategyTest {

    @Test
    void factoriesProduceExpectedModes() {
        assertEquals(BulkLoadStrategy.Mode.ORDERED, BulkLoadStrategy.ordered().mode());
        assertEquals(BulkLoadStrategy.Mode.UNORDERED_BULK, BulkLoadStrategy.unorderedBulk().mode());
    }

    @Test
    void onlyUnorderedBulkIsUnordered() {
        assertFalse(BulkLoadStrategy.ordered().isUnordered());
        assertTrue(BulkLoadStrategy.unorderedBulk().isUnordered());
    }

    @Test
    void nullModeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BulkLoadStrategy(null, false));
    }

    @Test
    void databaseConfigurationDefaultsToOrderedWhenUnset() {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(100, 10, new DefaultSupport(), Set.of());
        assertEquals(BulkLoadStrategy.ordered(), configuration.bulkLoadStrategy());
    }

    @Test
    void databaseConfigurationNormalizesNullStrategy() {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(100, 10, new DefaultSupport(), Set.of(), 42L, null, null);
        assertEquals(BulkLoadStrategy.ordered(), configuration.bulkLoadStrategy());
    }

    @Test
    void databaseConfigurationRetainsExplicitStrategy() {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(100, 10, new DefaultSupport(), Set.of(), 42L, null, BulkLoadStrategy.unorderedBulk());
        assertTrue(configuration.bulkLoadStrategy().isUnordered());
    }

    @Test
    void onlyPostgresAndMySqlSupportBulkLoad() {
        assertTrue(new PostgresSupport().supportsBulkLoad());
        assertTrue(new MySQLSupport().supportsBulkLoad());
        // CockroachDB extends PostgresSupport but cannot bulk load; it must override back to false
        assertFalse(new CockroachDBSupport().supportsBulkLoad());
        assertFalse(new DefaultSupport().supportsBulkLoad());
    }

    @Test
    void defaultSupportRejectsConstraintDisabling() {
        assertThrows(UnsupportedOperationException.class,
                () -> new DefaultSupport().disableConstraints(null, null));
    }
}
