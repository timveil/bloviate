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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies the {@link DatabaseConfiguration#seed()} contract end-to-end: the same schema filled
 * with the same seed yields identical data, and a different seed yields different data. The
 * non-key {@code code}/{@code label} columns are generated (the {@code serial id} is skipped), so
 * comparing them across fills exercises reproducibility.
 */
class PostgresSeedReproducibilityTest extends BasePostgresTest {

    @Test
    void sameSeedIsReproducibleAndDifferentSeedDiffers() throws SQLException {
        List<String> first = fillAndDump(42L);
        List<String> second = fillAndDump(42L);
        List<String> other = fillAndDump(99L);

        assertEquals(first, second, "same seed must produce identical data");
        assertNotEquals(first, other, "a different seed must produce different data");
    }

    private List<String> fillAndDump(long seed) throws SQLException {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(128, 20, new PostgresSupport(), null, seed);

        List<String> rows = new ArrayList<>();
        fillDatabase("create_column_config_test.postgres.sql", configuration, connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("select code, label from widget order by id")) {
                while (resultSet.next()) {
                    rows.add(resultSet.getInt("code") + "|" + resultSet.getString("label"));
                }
            }
        });
        return rows;
    }
}
