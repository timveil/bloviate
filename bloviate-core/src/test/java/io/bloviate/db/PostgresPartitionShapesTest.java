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
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.SqlTimestampGenerator;
import io.bloviate.util.DatabaseUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fills the shapes of declarative partitioning PostgreSQL offers (issue #615) through the partitioned
 * parent, against a real PostgreSQL with foreign-key enforcement on: LIST, HASH, RANGE with a DEFAULT
 * partition, multi-level, and a partitioned table on either side of a foreign key. Also pins how a fill
 * fails when the partition key lands outside every partition, and that partitioned tables compose with the
 * parallel, intra-table-partition, unordered-bulk, schema and table-selection features.
 *
 * <p>The schemas live in {@code create_partition_shapes.postgres.sql}; {@link PostgresPartitionedSchemaTest}
 * covers the #613 fixture. Not to be confused with the {@code partitions} setting of a
 * {@link TableConfiguration}, which splits one table's rows across workers (issue #447) and has nothing
 * to do with SQL partitioning; the two are combined in
 * {@link #intraTablePartitionsInsertThroughTheParentAndMatchASequentialFill()}.
 */
class PostgresPartitionShapesTest extends BaseDatabaseTestCase {

    private static final int ROWS = 40;

    private static PostgresSchemaFixture fixture;

    @BeforeAll
    static void startDatabase() {
        fixture = new PostgresSchemaFixture("create_partition_shapes.postgres.sql");
    }

    @AfterAll
    static void stopDatabase() {
        fixture.close();
    }

    @BeforeEach
    void emptySchemas() throws SQLException {
        for (String schema : List.of("part_list", "part_hash", "part_default", "part_multi", "part_rel", "part_direct")) {
            fixture.reset(schema);
        }
    }

    private static DatabaseConfiguration configuration(TableConfiguration... tableConfigurations) {
        return new DatabaseConfiguration(16, ROWS, new PostgresSupport(),
                tableConfigurations.length == 0 ? null : Set.of(tableConfigurations), 42L);
    }

    private static DatabaseConfiguration configuration(BulkLoadStrategy bulk, TableConfiguration... tableConfigurations) {
        return new DatabaseConfiguration(16, ROWS, new PostgresSupport(),
                tableConfigurations.length == 0 ? null : Set.of(tableConfigurations), 42L, null, bulk);
    }

    private static TableConfiguration timestamps(String table, String column, int fromYear, int toYear) {
        return new TableConfiguration(table, ROWS, Set.of(timestampColumn(column, fromYear, toYear)));
    }

    private static ColumnConfiguration timestampColumn(String column, int fromYear, int toYear) {
        return new ColumnConfiguration(column, random -> new SqlTimestampGenerator.Builder(random)
                .start(Timestamp.valueOf(LocalDateTime.of(fromYear, 1, 1, 0, 0)))
                .end(Timestamp.valueOf(LocalDateTime.of(toYear, 1, 1, 0, 0)))
                .build());
    }

    private static List<String> tableNames(Database database) {
        return database.tables().stream().map(Table::name).sorted().toList();
    }

    // ---------------------------------------------------------------------------------------------
    // discovery
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyPartitioningStrategyIsDiscoveredAsItsParentOnly() throws SQLException {
        Map<String, List<String>> expected = Map.of(
                "part_list", List.of("events"),
                "part_hash", List.of("accounts"),
                "part_default", List.of("logs"),
                "part_multi", List.of("metrics"),
                "part_rel", List.of("customers", "invoices", "payments"));
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            try (HikariDataSource dataSource = fixture.dataSource(entry.getKey());
                 Connection connection = dataSource.getConnection()) {
                assertEquals(entry.getValue(), tableNames(DatabaseUtils.getMetadata(connection)), entry.getKey());
            }
        }
    }

    @Test
    void everyLevelOfMultiLevelPartitioningMapsToTheTopLevelTable() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_multi");
             Connection connection = dataSource.getConnection()) {
            Map<String, String> partitions = new PostgresSupport().readPartitions(connection, "part_multi");

            // the intermediate partitions (metrics_2024, metrics_2024_b) are themselves partitioned tables to the driver
            assertEquals(Set.of("metrics_2024", "metrics_2024_a", "metrics_2024_b", "metrics_2024_b_0", "metrics_2024_b_1"),
                    partitions.keySet());
            assertEquals(Set.of("metrics"), Set.copyOf(partitions.values()));
        }
    }

    @Test
    void anotherSchemasPartitionsAreNotReportedForThisOne() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_hash");
             Connection connection = dataSource.getConnection()) {
            PostgresSupport support = new PostgresSupport();

            assertEquals(Set.of("accounts_0", "accounts_1", "accounts_2", "accounts_3"),
                    support.readPartitions(connection, "part_hash").keySet());
            // a null schema means the connection's current one
            assertEquals(support.readPartitions(connection, "part_hash"), support.readPartitions(connection, null));
            assertEquals(Set.of("invoices_2019_2020", "invoices_2020_2021"),
                    support.readPartitions(connection, "part_rel").keySet());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // the partitioning strategies
    // ---------------------------------------------------------------------------------------------

    @Test
    void hashPartitionedTableIsFilledWithoutAnyConfiguration() throws SQLException {
        fixture.fillSequential("part_hash", configuration());

        assertEquals(ROWS, fixture.count("part_hash.accounts"));
        assertEquals(ROWS, fixture.queryLong("select sum(c) from (select count(*) c from part_hash.accounts_0 "
                + "union all select count(*) from part_hash.accounts_1 union all select count(*) from part_hash.accounts_2 "
                + "union all select count(*) from part_hash.accounts_3) t"), "every row is in one of the partitions");
    }

    @Test
    void listPartitionedTableIsFilledWhenThePartitionKeyIsConstrained() throws SQLException {
        fixture.fillSequential("part_list", configuration(new TableConfiguration("events", ROWS,
                Set.of(new ColumnConfiguration("region", Distributions.<String>weighted(Map.of("eu", 1, "us", 1)))))));

        assertEquals(ROWS, fixture.count("part_list.events"));
        assertEquals(ROWS, fixture.count("part_list.events_eu") + fixture.count("part_list.events_us"));
        assertTrue(fixture.count("part_list.events_eu") > 0 && fixture.count("part_list.events_us") > 0,
                "rows are routed to both partitions");
    }

    /**
     * Without a constraint the generated region is arbitrary text that no partition accepts. The insert
     * fails and the driver's message says which table and which partition key: the per-table error the
     * fill reports, unwrapped.
     */
    @Test
    void aPartitionKeyOutsideEveryPartitionFailsNamingTheTableAndTheKey() throws SQLException {
        SQLException failure = assertThrows(SQLException.class,
                () -> fixture.fillSequential("part_list", configuration()));

        String message = failure.getMessage();
        assertTrue(message.contains("\"part_list\".\"events\""), message);
        assertTrue(message.contains("no partition of relation \"events\" found for row"), message);
        assertTrue(message.contains("Partition key of the failing row contains (region)"), message);
        assertEquals(0, fixture.count("part_list.events"));
    }

    @Test
    void aDefaultPartitionAcceptsUnconstrainedValues() throws SQLException {
        fixture.fillSequential("part_default", configuration());

        assertEquals(ROWS, fixture.count("part_default.logs"));
        assertEquals(ROWS, fixture.count("part_default.logs_other"), "the 2020 dates land in the DEFAULT partition");
        assertEquals(0, fixture.count("part_default.logs_2024"));
    }

    @Test
    void aConstrainedPartitionKeyLandsInTheNamedPartitionNotTheDefault() throws SQLException {
        fixture.fillSequential("part_default", configuration(timestamps("logs", "logged_at", 2024, 2025)));

        assertEquals(ROWS, fixture.count("part_default.logs_2024"));
        assertEquals(0, fixture.count("part_default.logs_other"));
    }

    @Test
    void multiLevelPartitionedTableIsFilledThroughTheTopLevelParent() throws SQLException {
        fixture.fillSequential("part_multi", configuration(new TableConfiguration("metrics", ROWS, Set.of(
                timestampColumn("taken_at", 2024, 2025),
                new ColumnConfiguration("site", random -> new IntegerGenerator.Builder(random).start(1).end(3).build())))));

        assertEquals(ROWS, fixture.count("part_multi.metrics"));
        assertEquals(ROWS, fixture.count("part_multi.metrics_2024"), "the year partition holds every row");
        assertEquals(ROWS, fixture.count("part_multi.metrics_2024_a") + fixture.count("part_multi.metrics_2024_b_0")
                + fixture.count("part_multi.metrics_2024_b_1"), "the rows reach the leaves");
    }

    // ---------------------------------------------------------------------------------------------
    // partitioned tables on either side of a foreign key
    // ---------------------------------------------------------------------------------------------

    @Test
    void aPartitionedTableWithAForeignKeyToAPlainTableAndAPlainTableReferencingItAreFilled() throws SQLException {
        fixture.fillSequential("part_rel", configuration());

        verifyPartRel();
    }

    @Test
    void theRelationshipsAreDescribedOncePerForeignKey() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_rel");
             Connection connection = dataSource.getConnection()) {
            Database database = DatabaseUtils.getMetadata(connection);

            // partitioned child -> plain parent
            List<ForeignKey> invoiceKeys = database.getTable("invoices").foreignKeys();
            assertEquals(1, invoiceKeys.size());
            assertEquals("customers", invoiceKeys.getFirst().primaryKey().tableName());
            // plain child -> partitioned parent, once against the parent and not once per partition
            List<ForeignKey> paymentKeys = database.getTable("payments").foreignKeys();
            assertEquals(1, paymentKeys.size());
            assertEquals("invoices", paymentKeys.getFirst().primaryKey().tableName());
            assertEquals(List.of("invoice_id", "issued_at"),
                    paymentKeys.getFirst().foreignKeyColumns().stream().map(key -> key.column().name()).toList());
            // the partitioned table's own primary key includes its partition key
            assertEquals(List.of("id", "issued_at"),
                    database.getTable("invoices").primaryKey().keyColumns().stream().map(key -> key.column().name()).toList());
        }
    }

    /**
     * PostgreSQL clones {@code child.a -> root(id)} onto each partition of {@code root} (two here), and the
     * driver lists every clone. They are the same foreign key re-pointed at a partition and are dropped;
     * {@code child.a -> part(code)} shares the referencing column but references a different column of a
     * partition, is the user's own, and must not be mistaken for a clone.
     */
    @Test
    void aForeignKeyDirectlyToAPartitionIsKeptWhileTheClonesOfTheParentKeyAreDropped() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_direct");
             Connection connection = dataSource.getConnection()) {
            assertEquals(4, fixture.queryLong("select count(*) from pg_constraint c join pg_namespace n on n.oid = c.connamespace "
                    + "where c.contype = 'f' and n.nspname = 'part_direct'"),
                    "root(id), its two clones, and the direct key to part(code)");

            List<ForeignKey> foreignKeys = DatabaseUtils.getMetadata(connection).getTable("child").foreignKeys();

            assertEquals(List.of("part", "root"),
                    foreignKeys.stream().map(key -> key.primaryKey().tableName()).sorted().toList(),
                    "one key to the parent (its clones dropped) and the direct key to the partition");
        }
    }

    @Test
    void aForeignKeyDirectlyToAPartitionFailsTheFillNamingThePartition() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_direct");
             Connection connection = dataSource.getConnection()) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> new DatabaseFiller.Builder(connection, configuration()).build().fill());

            assertTrue(failure.getMessage().contains("references table [part]"), failure.getMessage());
            assertTrue(failure.getMessage().contains("Nothing was written"), failure.getMessage());
        }
        assertEquals(0, fixture.count("part_direct.root"));
    }

    private static void verifyPartRel() throws SQLException {
        assertEquals(ROWS, fixture.count("part_rel.customers"));
        assertEquals(ROWS, fixture.count("part_rel.invoices"));
        assertEquals(ROWS, fixture.count("part_rel.payments"));
        assertEquals(ROWS, fixture.count("part_rel.invoices_2019_2020") + fixture.count("part_rel.invoices_2020_2021"));
        fixture.assertForeignKeysEnforced("part_rel", 2);
    }

    private static String dump(String table, String orderBy) throws SQLException {
        return fixture.queryString("select coalesce(string_agg(t::text, '|' order by " + orderBy + "), '') from " + table + " t");
    }

    // ---------------------------------------------------------------------------------------------
    // composition with the other fill features
    // ---------------------------------------------------------------------------------------------

    @Test
    void parallelFillWithThreeThreadsFillsPartitionedTables() throws SQLException {
        fixture.fillParallel("part_rel", configuration(), 3);

        verifyPartRel();
    }

    @Test
    void intraTablePartitionsInsertThroughTheParentAndMatchASequentialFill() throws SQLException {
        int invoices = 120;
        DatabaseConfiguration sequential = configuration(
                new TableConfiguration("customers", ROWS), new TableConfiguration("invoices", invoices),
                new TableConfiguration("payments", ROWS));
        fixture.fillSequential("part_rel", sequential);
        String sequentialInvoices = dump("part_rel.invoices", "t.id");
        String sequentialPayments = dump("part_rel.payments", "t.id");
        assertEquals(invoices, fixture.count("part_rel.invoices"));

        fixture.reset("part_rel");
        // the row range of "invoices" is split across three workers, each inserting through the parent
        DatabaseConfiguration split = configuration(
                new TableConfiguration("customers", ROWS), new TableConfiguration("invoices", invoices, 3),
                new TableConfiguration("payments", ROWS));
        fixture.fillParallel("part_rel", split, 3);

        assertEquals(invoices, fixture.count("part_rel.invoices"));
        assertEquals(invoices, fixture.count("part_rel.invoices_2019_2020") + fixture.count("part_rel.invoices_2020_2021"));
        assertEquals(sequentialInvoices, dump("part_rel.invoices", "t.id"), "row-range workers produce the sequential rows");
        assertEquals(sequentialPayments, dump("part_rel.payments", "t.id"));
        fixture.assertForeignKeysEnforced("part_rel", 2);
    }

    @Test
    void unorderedBulkFillMatchesTheOrderedFillOfAPartitionedSchema() throws SQLException {
        fixture.fillParallel("part_rel", configuration(BulkLoadStrategy.ordered()), 3);
        String orderedInvoices = dump("part_rel.invoices", "t.id");
        String orderedPayments = dump("part_rel.payments", "t.id");
        verifyPartRel();

        fixture.reset("part_rel");
        fixture.fillParallel("part_rel", configuration(BulkLoadStrategy.unorderedBulk()), 3);

        verifyPartRel();
        assertEquals(orderedInvoices, dump("part_rel.invoices", "t.id"));
        assertEquals(orderedPayments, dump("part_rel.payments", "t.id"));
    }

    @Test
    void theSchemaBuilderOptionFillsAPartitionedSchemaThatIsNotTheConnectionsCurrentOne() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("public")) {
            new DatabaseFiller.Builder(dataSource, configuration()).schema("part_hash").build().fill();
        }

        assertEquals(ROWS, fixture.count("part_hash.accounts"));
    }

    @Test
    void theSchemaBuilderOptionAlsoWorksOnTheParallelPath() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("public")) {
            new DatabaseFiller.Builder(dataSource, configuration()).schema("part_rel").threads(3).build().fill();
        }

        verifyPartRel();
    }

    // ---------------------------------------------------------------------------------------------
    // table selection and table configuration
    // ---------------------------------------------------------------------------------------------

    @Test
    void thePartitionedParentCanBeSelectedByName() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_hash");
             Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration()).includeTables("accounts").build().fill();
        }

        assertEquals(ROWS, fixture.count("part_hash.accounts"));
    }

    @Test
    void thePartitionedParentCanBeExcludedByName() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_rel");
             Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration()).excludeTables("payments", "invoices").build().fill();
        }

        assertEquals(ROWS, fixture.count("part_rel.customers"));
        assertEquals(0, fixture.count("part_rel.invoices"));
    }

    @Test
    void aWildcardOverTheParentAndItsPartitionsFillsTheParentOnly() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_hash");
             Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration()).includeTables("accounts*").build().fill();
        }

        assertEquals(ROWS, fixture.count("part_hash.accounts"));
    }

    /** A partition is never a fill target, so selecting only it selects nothing, and says so. */
    @Test
    void includingOnlyAPartitionLeavesNothingToFill() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_hash");
             Connection connection = dataSource.getConnection()) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> new DatabaseFiller.Builder(connection, configuration()).includeTables("accounts_0").build().fill());

            assertTrue(failure.getMessage().contains("leave no table to fill"), failure.getMessage());
            assertTrue(failure.getMessage().contains("accounts"), failure.getMessage());
        }
        assertEquals(0, fixture.count("part_hash.accounts"));
    }

    @Test
    void includingAPartitionAlongsideItsParentFillsTheParentAndIgnoresThePartitionPattern() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_default");
             Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration()).includeTables("logs", "logs_2024").build().fill();
        }

        assertEquals(ROWS, fixture.count("part_default.logs"));
    }

    @Test
    void aTableConfigurationForAPartitionIsClassifiedAsBelongingToItsParent() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_default");
             Connection connection = dataSource.getConnection()) {
            List<String> discovered = new java.util.ArrayList<>();
            Map<String, String> partitions = new java.util.LinkedHashMap<>();
            Database database = DatabaseUtils.getMetadata(connection, new PostgresSupport(), (names, found) -> {
                discovered.addAll(names);
                partitions.putAll(found);
                return names;
            });
            DatabaseConfiguration configuration = configuration(
                    new TableConfiguration("logs", 5),
                    new TableConfiguration("LOGS_2024", 5),
                    new TableConfiguration("no_such_table", 5));

            DatabaseFiller.UnusedTableConfigurations unused =
                    DatabaseFiller.findUnusedTableConfigurations(database, discovered, partitions, configuration);

            assertEquals(List.of("no_such_table"), unused.unknown());
            assertEquals(List.of(), unused.excluded());
            assertEquals(Map.of("LOGS_2024", "logs"), unused.partitions());
        }
    }

    @Test
    void aTableConfigurationForAPartitionDoesNotFailTheFillAndIsNotApplied() throws SQLException {
        try (HikariDataSource dataSource = fixture.dataSource("part_default");
             Connection connection = dataSource.getConnection()) {
            new DatabaseFiller.Builder(connection, configuration(new TableConfiguration("logs_2024", 3))).build().fill();
        }

        assertEquals(ROWS, fixture.count("part_default.logs"), "the row count is the default one, not the partition's 3");
    }
}
