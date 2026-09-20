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
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 * <p>Observed on PostgreSQL 18 when #614 was written (the {@code @Disabled} tests fail this way today):
 * <ul>
 *   <li>a leaf partition filled directly: {@code new row for relation "orders_2024_01" violates
 *       partition constraint} (the generated {@code placed_at} was 2019-12-15);</li>
 *   <li>a foreign key to a partitioned parent: {@code IllegalArgumentException: table with name
 *       [orders] not found};</li>
 *   <li>a foreign key to {@code UNIQUE (tenant_id, id)}: {@code violates foreign key constraint
 *       "projects_tenant_id_customer_id_fkey"};</li>
 *   <li>a column shared by two foreign keys: the first FK (regions) is satisfied and the second is
 *       not, {@code violates foreign key constraint "shipments_tenant_id_warehouse_id_fkey"};</li>
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

    private static DatabaseConfiguration configuration() {
        return new DatabaseConfiguration(16, ROWS, new PostgresSupport(), null, 42L);
    }

    // ---------------------------------------------------------------------------------------------
    // partitioned tables (#615)
    // ---------------------------------------------------------------------------------------------

    /**
     * Discovery asks JDBC for type {@code TABLE} only. PostgreSQL reports a partitioned parent as
     * {@code PARTITIONED TABLE} and its leaf partitions as plain {@code TABLE}s, so the parent is
     * skipped and every leaf is treated as an independent, unconstrained table.
     */
    @Test
    void partitionedParentIsSkippedAndLeafPartitionsAreDiscovered() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("orders_part");
             Connection connection = dataSource.getConnection()) {

            Database database = DatabaseUtils.getMetadata(connection);
            List<String> names = new ArrayList<>();
            for (Table table : database.tables()) {
                names.add(table.name());
            }

            assertEquals(Set.of("orders_2024_01", "orders_2024_02", "orders_2024_03", "order_stats"), Set.copyOf(names),
                    "the partitioned parent [orders] must not be discovered; its leaves and the rollup are");

            // the reason: the driver reports the parent under a different table type
            try (ResultSet rs = connection.getMetaData().getTables(null, "orders_part", "orders", null)) {
                rs.next();
                assertEquals("PARTITIONED TABLE", rs.getString("TABLE_TYPE"));
            }
        }
    }

    /**
     * Desired outcome for #615: a range-partitioned table is filled through its parent, so every row
     * routes to a partition (which requires the generated {@code placed_at} to fall inside the
     * bounded range the leaves cover).
     *
     * <p>Today each leaf is filled directly with default, unbounded timestamps, which violates the
     * partition constraint.
     */
    @Test
    @Disabled("#615: leaf partitions are filled directly with unbounded timestamps that violate partition bounds")
    void partitionedTableIsFilledThroughItsParent() throws SQLException {
        fixture.fillSequential("orders_part", configuration());

        verifyPartitionedOrders("orders_part");
    }

    /**
     * Desired outcome for #615: a foreign key that references a partitioned table resolves to it.
     * Today {@code Database.getTable} does not know the (skipped) parent and throws.
     */
    @Test
    @Disabled("#615: a foreign key to a partitioned parent fails table lookup (IllegalArgumentException)")
    void foreignKeyToPartitionedParentIsFilled() throws SQLException {
        fixture.fillSequential("fk_to_part", configuration());

        verifyForeignKeyToPartitionedParent();
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
     * Desired outcome for #617: an FK to {@code UNIQUE (tenant_id, id)} pairs each FK column with the
     * column it actually references. Today FK columns are matched to the parent's primary key
     * {@code (id)} by position, so {@code tenant_id} is seeded from {@code customers.id} and
     * {@code customer_id} from nothing.
     */
    @Test
    @Disabled("#617: FK to a UNIQUE key is mapped by position onto the primary key's columns")
    void foreignKeyToUniqueKeyIsFilled() throws SQLException {
        fixture.fillSequential("tenant_unique", configuration());

        verifyTenantUnique();
    }

    /**
     * Desired outcome for #617: a column shared by two foreign keys satisfies both. Both FKs here
     * reference composite primary keys, so positional matching is right and only sharing is in
     * question. Today only the first FK seeds {@code tenant_id}.
     */
    @Test
    @Disabled("#617: a column shared by two foreign keys is seeded from only the first")
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
     * Desired outcome for #619: a {@code date_trunc('month', d) = d} column is filled with first-of-month
     * dates. Today the quoted argument is read as the allowed value {@code month} and the engine binds
     * the string to a {@code date}, which PostgreSQL rejects.
     */
    @Test
    @Disabled("#619: date_trunc('month', d) = d is misread as the allowed value 'month' and bound to a date column")
    void dateTruncFirstOfMonthCheckIsSatisfied() throws SQLException {
        fixture.fillSequential("check_trunc", configuration());

        assertEquals(ROWS, fixture.count("check_trunc.invoices"));
        assertEquals(0, fixture.queryLong("select count(*) from check_trunc.invoices "
                + "where billing_month <> date_trunc('month', billing_month)"));
    }

    /**
     * Desired outcome for #619: an {@code EXTRACT(day FROM d) = 1} column is filled with first-of-month
     * dates. Today the expression is (correctly) not parsed, so a random date is generated and the
     * insert violates the check.
     */
    @Test
    @Disabled("#619: EXTRACT(day FROM d) = 1 is not recognised, so random dates violate the CHECK")
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
     * It needs #615 (orders), #617 (tenant FKs) and #619 (invoices), so it can only be enabled once
     * all three have landed. The {@code rollup} table is filled like any other; it is not derived.
     */
    @Test
    @Disabled("#613: needs #615 (partitioned orders), #617 (tenant-scoped FKs) and #619 (first-of-month CHECKs)")
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
