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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads PostgreSQL's declarative partitions (issue #615): every relation with
 * {@code pg_class.relispartition} set, paired with the top-level partitioned table it hangs off.
 *
 * <p>A recursive walk of {@code pg_inherits} finds the top-level table, so an intermediate partition
 * of a multi-level scheme (itself partitioned, so the JDBC driver lists it as a
 * {@code PARTITIONED TABLE}) is reported like a leaf and only the outermost table is left to fill. The
 * walk uses only {@code relispartition} (PostgreSQL 10+) rather than {@code pg_partition_root}
 * (PostgreSQL 13+), so it is safe on every version the driver supports. Tables that merely
 * {@code INHERITS} from another are not declarative partitions and are not reported.
 *
 * @since 3.6.0
 */
final class PostgresPartitions {

    private static final String SQL = """
            WITH RECURSIVE partition_root (relid, root) AS (
                SELECT i.inhrelid, i.inhparent
                FROM pg_catalog.pg_inherits i
                JOIN pg_catalog.pg_class child ON child.oid = i.inhrelid
                JOIN pg_catalog.pg_class parent ON parent.oid = i.inhparent
                WHERE child.relispartition AND NOT parent.relispartition
              UNION ALL
                SELECT i.inhrelid, r.root
                FROM pg_catalog.pg_inherits i
                JOIN partition_root r ON i.inhparent = r.relid
            )
            SELECT child_ns.nspname AS partition_schema, child.relname AS partition_name,
                   root_ns.nspname AS root_schema, root.relname AS root_name
            FROM partition_root r
            JOIN pg_catalog.pg_class child ON child.oid = r.relid
            JOIN pg_catalog.pg_namespace child_ns ON child_ns.oid = child.relnamespace
            JOIN pg_catalog.pg_class root ON root.oid = r.root
            JOIN pg_catalog.pg_namespace root_ns ON root_ns.oid = root.relnamespace
            WHERE child.relkind IN ('r', 'p', 'f') AND child_ns.nspname = COALESCE(CAST(? AS text), current_schema()::text)
            ORDER BY child.relname""";

    private PostgresPartitions() {
    }

    /**
     * Reads the partitions of one schema.
     *
     * @param connection an open connection to query the catalog with
     * @param schema     the schema to read, or null/blank for the connection's current schema
     * @return partition name to the name of its top-level partitioned table, in name order; a
     *         top-level table in another schema is written {@code schema.table}
     * @throws SQLException if the catalog query fails
     */
    static Map<String, String> read(Connection connection, String schema) throws SQLException {
        Map<String, String> partitions = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, schema == null || schema.isBlank() ? null : schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String partitionSchema = rs.getString("partition_schema");
                    String rootSchema = rs.getString("root_schema");
                    String root = rs.getString("root_name");
                    partitions.put(rs.getString("partition_name"),
                            partitionSchema.equals(rootSchema) ? root : rootSchema + "." + root);
                }
            }
        }
        return partitions;
    }
}
