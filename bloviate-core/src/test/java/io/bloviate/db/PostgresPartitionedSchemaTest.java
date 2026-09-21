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

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.gen.SqlTimestampGenerator;
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the engine's behaviour for the schema shapes in issue #613 &mdash; a range-partitioned table,
 * tenant-scoped composite foreign keys and first-of-month {@code CHECK}s &mdash; against a real
 * PostgreSQL (issue #614). Each scenario has its own schema in {@code create_partitioned.postgres.sql}
 * so a failure in one cannot mask another.
 *
 * <p>Convention: a case that works today is a normal test. A case that fails or is unsupported today is
 * a test of the <em>desired</em> outcome annotated {@code @Disabled("#NNN: reason")}; the pull request
 * that fixes #NNN removes the annotation. Every desired outcome is asserted with foreign-key
 * enforcement genuinely on (see {@link PostgresSchemaFixture#assertForeignKeysEnforced}), so passing
 * means the data is valid, not that validation was skipped.
 *
 * <p>Observed on PostgreSQL 18 when #614 was written (the {@code @Disabled} tests failed this way,
 * except the two {@code CHECK} cases, which #619 has since fixed and enabled; the two partitioned-table
 * cases are fixed and enabled by #615):
 * <ul>
 *   <li>a leaf partition filled directly: {@code new row for relation "orders_2024_01" violates
 *       partition constraint} (the generated {@code placed_at} was 2019-12-15);</li>
 *   <li>a foreign key to a partitioned parent: {@code IllegalArgumentException: table with name
 *       [orders] not found};</li>
 *   <li>a foreign key to {@code UNIQUE (tenant_id, id)}: {@code violates foreign key constraint
 *       "projects_tenant_id_customer_id_fkey"} &mdash; fixed and enabled by #617;</li>
 *   <li>a column shared by two foreign keys: the first FK (regions) was satisfied and the second was
 *       not, {@code violates foreign key constraint "shipments_tenant_id_warehouse_id_fkey"} &mdash;
 *       fixed and enabled by #617;</li>
 *   <li>{@code date_trunc('month', d) = d}: {@code invalid input syntax for type date: "month"};</li>
 *   <li>{@code EXTRACT(day FROM d) = 1}: {@code violates check constraint
 *       "statements_period_start_check"} (a random date such as 2019-12-29 was generated).</li>
 * </ul>
 */
class PostgresPartitionedSchemaTest extends BaseDatabaseTestCase {

    private static final int ROWS = 40;

    private static PostgresSchemaFixture fixture;

    @BeforeAll
    static void startDatabase() {
        fixture = new PostgresSchemaFixture("create_partitioned.postgres.sql");
    }

    @AfterAll
    static void stopDatabase() {
        fixture.close();
    }

    @BeforeEach
    void emptySchemas() throws SQLException {
        for (String schema : List.of("saas", "orders_part", "fk_to_part", "tenant_unique", "tenant_shared",
                "composite_pk", "check_trunc", "check_extract")) {
            fixture.reset(schema);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // partitioned tables (#615)
    // ---------------------------------------------------------------------------------------------

    /**
     * The driver reports a partitioned parent as {@code PARTITIONED TABLE} and its leaf partitions as
     * plain {@code TABLE}s. Discovery asks for both and drops every partition, so the parent is the
     * table to fill and its leaves are not tables of the metadata at all (#615). Before #615 this
     * discovered only the leaves and skipped the parent.
     */
    @Test
    void partitionedParentIsDiscoveredAndItsPartitionsAreNot() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("orders_part");
             Connection connection = dataSource.getConnection()) {

            Database database = DatabaseUtils.getMetadata(connection);
            List<String> names = new ArrayList<>();
            for (Table table : database.tables()) {
                names.add(table.name());
            }

            assertEquals(Set.of("orders", "order_stats"), Set.copyOf(names),
                    "the partitioned parent [orders] is discovered; its leaves are not");

            // the parent's metadata is complete: columns, and a primary key that includes the partition key
            Table orders = database.getTable("orders");
            assertEquals(List.of("id", "tenant_id", "placed_at", "total"), orders.columns().stream().map(Column::name).toList());
            assertEquals(List.of("id", "placed_at"), orders.primaryKey().keyColumns().stream().map(k -> k.column().name()).toList());

            // the driver still reports the parent under a different table type
            try (ResultSet rs = connection.getMetaData().getTables(null, "orders_part", "orders", null)) {
                rs.next();
                assertEquals("PARTITIONED TABLE", rs.getString("TABLE_TYPE"));
            }

            assertEquals(Map.of("orders_2024_01", "orders", "orders_2024_02", "orders", "orders_2024_03", "orders"),
                    new PostgresSupport().readPartitions(connection, "orders_part"));
        }
    }

    /**
     * #615: a range-partitioned table is filled through its parent, so every row routes to a partition
     * (which requires the generated {@code placed_at} to fall inside the bounded range the leaves
     * cover; the default 2020 +-100 day range does not, so the column is configured).
     */
    @Test
    void partitionedTableIsFilledThroughItsParent() throws SQLException {
        fixture.fillSequential("orders_part", configuration(placedAtIn2024("orders", 4)));

        verifyPartitionedOrders("orders_part");
    }

    /**
     * #615: a foreign key that references a partitioned table resolves to it. The referencing column
     * is generated by its own column's generator (seeded from the parent's key), so it carries the same
     * range as the partition key it references.
     */
    @Test
    void foreignKeyToPartitionedParentIsFilled() throws SQLException {
        fixture.fillSequential("fk_to_part", configuration(placedAtIn2024("orders", 3), placedAtIn2024("order_items", 3)));

        verifyForeignKeyToPartitionedParent();
    }

    /**
     * #615: the foreign key is described once, against the partitioned table. PostgreSQL clones it onto
     * every partition of the referenced table and the driver lists each clone; those are dropped.
     */
    @Test
    void foreignKeyToPartitionedParentIsDescribedOnceAgainstTheParent() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("fk_to_part");
             Connection connection = dataSource.getConnection()) {
            assertEquals(3, fixture.queryLong("select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                    + "where c.contype = 'f' and n.nspname = 'fk_to_part'"), "the database holds a parent constraint and one clone per partition");

            Database database = DatabaseUtils.getMetadata(connection);

            assertEquals(Set.of("orders", "order_items"), database.tables().stream().map(Table::name).collect(Collectors.toSet()));
            List<ForeignKey> foreignKeys = database.getTable("order_items").foreignKeys();
            assertEquals(1, foreignKeys.size());
            assertEquals("orders", foreignKeys.getFirst().primaryKey().tableName());
            assertEquals(List.of("id", "placed_at"),
                    foreignKeys.getFirst().primaryKey().keyColumns().stream().map(k -> k.column().name()).toList());
        }
    }

    private static DatabaseConfiguration configuration(TableConfiguration... tableConfigurations) {
        return new DatabaseConfiguration(16, ROWS, new PostgresSupport(),
                tableConfigurations.length == 0 ? null : Set.of(tableConfigurations), 42L);
    }

    /** Constrains {@code placed_at} of {@code table} to 2024-01-01 up to the first of {@code endMonth}, the range the partitions cover. */
    private static TableConfiguration placedAtIn2024(String table, int endMonth) {
        return new TableConfiguration(table, ROWS, Set.of(new ColumnConfiguration("placed_at",
                random -> new SqlTimestampGenerator.Builder(random)
                        .start(Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 0, 0)))
                        .end(Timestamp.valueOf(LocalDateTime.of(2024, endMonth, 1, 0, 0)))
                        .build())));
    }

    static void verifyPartitionedOrders(String schema) throws SQLException {
        assertEquals(ROWS, fixture.count(schema + ".orders"));
        assertEquals(0, fixture.queryLong("select count(*) from " + schema + ".orders "
                + "where placed_at < timestamp '2024-01-01' or placed_at >= timestamp '2024-04-01'"));
    }

    static void verifyForeignKeyToPartitionedParent() throws SQLException {
        assertEquals(ROWS, fixture.count("fk_to_part.orders"));
        assertEquals(ROWS, fixture.count("fk_to_part.order_items"));
        fixture.assertForeignKeysEnforced("fk_to_part", 1);
    }

    // ---------------------------------------------------------------------------------------------
    // tenant-scoped composite foreign keys (#617)
    // ---------------------------------------------------------------------------------------------

    /** Control: a composite FK to a composite PRIMARY KEY works (it is what TPC-C uses). */
    @Test
    void compositeForeignKeyToCompositePrimaryKeyIsFilled() throws SQLException {
        fixture.fillSequential("composite_pk", configuration());

        assertEquals(ROWS, fixture.count("composite_pk.parent"));
        assertEquals(ROWS, fixture.count("composite_pk.child"));
        fixture.assertForeignKeysEnforced("composite_pk", 1);
    }

    /**
     * An FK to {@code UNIQUE (tenant_id, id)} pairs each FK column with the column it actually
     * references. FK columns used to be matched to the parent's primary key {@code (id)} by position,
     * so {@code tenant_id} was filled from {@code customers.id} and {@code customer_id} from nothing.
     */
    @Test
    void foreignKeyToUniqueKeyIsFilled() throws SQLException {
        fixture.fillSequential("tenant_unique", configuration());

        verifyTenantUnique();
    }

    /**
     * A column shared by two foreign keys satisfies both. Both FKs here reference composite primary
     * keys, so positional matching is right and only sharing is in question: {@code tenant_id} used to
     * be filled from the first FK alone, leaving the second with nothing to match. Since #617 the
     * columns a shared key ties together are filled from one seed, so {@code regions.tenant_id} and
     * {@code warehouses.tenant_id} carry the same values and a shipment's tenant is in both.
     */
    @Test
    void columnSharedByTwoForeignKeysSatisfiesBoth() throws SQLException {
        fixture.fillSequential("tenant_shared", configuration());

        verifyTenantShared();
    }

    static void verifyTenantUnique() throws SQLException {
        assertEquals(ROWS, fixture.count("tenant_unique.customers"));
        assertEquals(ROWS, fixture.count("tenant_unique.projects"));
        fixture.assertForeignKeysEnforced("tenant_unique", 1);
    }

    static void verifyTenantShared() throws SQLException {
        assertEquals(ROWS, fixture.count("tenant_shared.regions"));
        assertEquals(ROWS, fixture.count("tenant_shared.warehouses"));
        assertEquals(ROWS, fixture.count("tenant_shared.shipments"));
        fixture.assertForeignKeysEnforced("tenant_shared", 2);
    }

    // ---------------------------------------------------------------------------------------------
    // CHECK constraints on dates (#619)
    // ---------------------------------------------------------------------------------------------

    /**
     * Not a bug in itself, but the fact the parser has to cope with: PostgreSQL stores the check with
     * the {@code 'month'} literal quoted, the column cast to {@code timestamp with time zone}, and no
     * {@code <} or {@code >} anywhere. See {@code PostgresConstraintsTest} for the parser's reading.
     */
    @Test
    void firstOfMonthCheckIsStoredWithAQuotedFunctionArgument() throws SQLException {
        assertEquals("CHECK ((date_trunc('month'::text, (billing_month)::timestamp with time zone) = billing_month))",
                fixture.queryString("select pg_get_constraintdef(c.oid) from pg_constraint c "
                        + "join pg_namespace n on n.oid = c.connamespace where c.contype = 'c' and n.nspname = 'check_trunc'"));
    }

    @Test
    void extractDayCheckIsStoredAsAnEqualityAgainstANumericCast() throws SQLException {
        assertEquals("CHECK ((EXTRACT(day FROM period_start) = (1)::numeric))",
                fixture.queryString("select pg_get_constraintdef(c.oid) from pg_constraint c "
                        + "join pg_namespace n on n.oid = c.connamespace where c.contype = 'c' and n.nspname = 'check_extract'"));
    }

    /**
     * #619: a {@code date_trunc('month', d) = d} column is filled with first-of-month dates. The quoted
     * argument used to be read as the allowed value {@code month}, which the engine bound to a
     * {@code date} and PostgreSQL rejected.
     */
    @Test
    void dateTruncFirstOfMonthCheckIsSatisfied() throws SQLException {
        fixture.fillSequential("check_trunc", configuration());

        assertEquals(ROWS, fixture.count("check_trunc.invoices"));
        assertEquals(0, fixture.queryLong("select count(*) from check_trunc.invoices "
                + "where billing_month <> date_trunc('month', billing_month)"));
    }

    /**
     * #619: an {@code EXTRACT(day FROM d) = 1} column is filled with first-of-month dates. The
     * expression used to be (correctly) not parsed, so a random date was generated and the insert
     * violated the check.
     */
    @Test
    void extractDayFirstOfMonthCheckIsSatisfied() throws SQLException {
        fixture.fillSequential("check_extract", configuration());

        assertEquals(ROWS, fixture.count("check_extract.statements"));
        assertEquals(0, fixture.queryLong("select count(*) from check_extract.statements "
                + "where extract(day from period_start) <> 1"));
    }

    // ---------------------------------------------------------------------------------------------
    // the whole of #613
    // ---------------------------------------------------------------------------------------------

    /**
     * End-to-end target for the umbrella: the whole {@code saas} schema fills with enforcement on.
     * It needs #617 (tenant FKs), so it can only be enabled once that has landed (#615, the
     * partitioned orders, and #619, the invoices' first-of-month CHECKs, have; it will also need the
     * partition key of {@code orders} constrained, see {@link #placedAtIn2024}). The {@code rollup} table is filled like any other; it is not derived.
     */
    @Test
    @Disabled("#613: the tenant-scoped FKs (#617), partitioned tables (#615) and first-of-month CHECKs "
            + "(#619) are done; this still needs saas.orders' partition key constrained to the range its "
            + "leaves cover, as placedAtIn2024 does for the single-scenario schemas")
    void wholeMotivatingSchemaIsFilled() throws SQLException {
        fixture.fillSequential("saas", configuration());

        for (String table : List.of("customers", "projects", "tasks", "orders", "invoices", "order_stats")) {
            assertEquals(ROWS, fixture.count("saas." + table), table);
        }
        fixture.assertForeignKeysEnforced("saas", 3);
        verifyPartitionedOrders("saas");
        assertEquals(0, fixture.queryLong("select count(*) from saas.invoices "
                + "where billing_month <> date_trunc('month', billing_month) or extract(day from period_start) <> 1"));
    }
}
