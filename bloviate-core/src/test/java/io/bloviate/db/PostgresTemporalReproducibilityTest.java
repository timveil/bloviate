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

import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies that <strong>temporal</strong> columns honor the {@link DatabaseConfiguration#seed()}
 * reproducibility contract: the same schema filled with the same seed yields identical
 * date/time/timestamp/timestamptz values, and a different seed yields different ones.
 *
 * <p>Regression test for the bug where the default date/time/timestamp/instant generators anchored
 * their range to {@code Instant.now()}, so temporal values carried wall-clock jitter and differed
 * between runs even with a fixed seed. The {@code standard_table} fixture has {@code date},
 * {@code time}, {@code timestamp} and {@code timestamptz} columns, so dumping them across fills
 * exercises every default temporal generator.
 */
class PostgresTemporalReproducibilityTest extends BasePostgresTest {

    @Test
    void temporalColumnsAreReproducibleForTheSameSeed() throws SQLException {
        List<String> first = fillAndDumpTemporal(42L);
        List<String> second = fillAndDumpTemporal(42L);
        List<String> other = fillAndDumpTemporal(99L);

        assertFalse(first.isEmpty(), "fixture should generate rows to compare");
        assertEquals(first, second, "same seed must produce identical temporal data");
        assertNotEquals(first, other, "a different seed must produce different temporal data");
    }

    private List<String> fillAndDumpTemporal(long seed) throws SQLException {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(128, 25, new PostgresSupport(), null, seed);

        List<String> rows = new ArrayList<>();
        fillDatabase("create_tables.postgres.sql", configuration, connection -> {
            // k=date, l=time, m=timestamp, n=timestamptz on the standard_table fixture
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "select k, l, m, n from standard_table order by id")) {
                while (resultSet.next()) {
                    rows.add(resultSet.getString("k") + "|" + resultSet.getString("l") + "|"
                            + resultSet.getString("m") + "|" + resultSet.getString("n"));
                }
            }
        });
        return rows;
    }
}
