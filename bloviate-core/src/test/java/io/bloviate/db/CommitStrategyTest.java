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

import io.bloviate.ext.DefaultSupport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitStrategyTest {

    @Test
    void factoriesProduceExpectedModes() {
        assertEquals(CommitStrategy.Mode.CONNECTION_DEFAULT, CommitStrategy.connectionDefault().mode());
        assertEquals(CommitStrategy.Mode.PER_TABLE, CommitStrategy.perTable().mode());

        CommitStrategy everyFive = CommitStrategy.everyNBatches(5);
        assertEquals(CommitStrategy.Mode.EVERY_N_BATCHES, everyFive.mode());
        assertEquals(5, everyFive.batches());
    }

    @Test
    void onlyConnectionDefaultLeavesTransactionToTheConnection() {
        assertFalse(CommitStrategy.connectionDefault().managesTransaction());
        assertTrue(CommitStrategy.perTable().managesTransaction());
        assertTrue(CommitStrategy.everyNBatches(2).managesTransaction());
    }

    @Test
    void everyNBatchesRejectsNonPositiveCadence() {
        assertThrows(IllegalArgumentException.class, () -> CommitStrategy.everyNBatches(0));
        assertThrows(IllegalArgumentException.class, () -> CommitStrategy.everyNBatches(-1));
    }

    @Test
    void databaseConfigurationDefaultsToConnectionDefaultWhenUnset() {
        // the four-arg convenience constructor must normalize the (absent) strategy
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(100, 10, new DefaultSupport(), Set.of());
        assertEquals(CommitStrategy.connectionDefault(), configuration.commitStrategy());
    }

    @Test
    void databaseConfigurationNormalizesNullStrategy() {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(100, 10, new DefaultSupport(), Set.of(), 42L, null);
        assertEquals(CommitStrategy.connectionDefault(), configuration.commitStrategy());
    }
}
