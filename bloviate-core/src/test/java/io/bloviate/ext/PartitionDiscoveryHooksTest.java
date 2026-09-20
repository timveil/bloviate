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

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The partition-discovery hooks of {@link DatabaseSupport} (issue #615) are opt-in: only PostgreSQL asks
 * for {@code PARTITIONED TABLE} and reads partitions, so every other support discovers exactly the tables
 * it did before and excludes nothing. The catalog query itself is covered against a real PostgreSQL in
 * {@code PostgresPartitionShapesTest}.
 */
class PartitionDiscoveryHooksTest {

    @Test
    void everySupportOtherThanPostgresDiscoversPlainTablesOnlyAndExcludesNothing() throws SQLException {
        List<DatabaseSupport> supports = List.of(new DefaultSupport(), new MySQLSupport(), new MariaDBSupport(),
                new CockroachDBSupport(), new H2Support(), new SQLiteSupport(), new BigQuerySupport());

        for (DatabaseSupport support : supports) {
            String name = support.getClass().getSimpleName();
            assertEquals(List.of("TABLE"), support.discoveredTableTypes(), name);
            // no connection is needed (or used) when there is nothing to read
            assertEquals(Map.of(), support.readPartitions(null, "some_schema"), name);
        }
    }

    @Test
    void postgresAlsoDiscoversPartitionedTables() {
        assertEquals(List.of("TABLE", "PARTITIONED TABLE"), new PostgresSupport().discoveredTableTypes());
    }
}
