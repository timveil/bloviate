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

package io.bloviate.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@code bloviate fill} in-process against in-memory H2 (no Docker, no {@code System.exit}) and
 * checks the exit code, what was printed, and what ended up in the database.
 *
 * <p>The schema is a {@code customers} / {@code orders} parent-child pair, a derived
 * {@code order_stats} table and an {@code audit} table the hook scripts write to, so the order and the
 * effect of {@code --before}/{@code --after} are visible in the data.
 */
class FillCommandTest {

    private static final String SCHEMA = H2Database.ORDERS_SCHEMA + """
            create table audit (seq int auto_increment primary key, note varchar(10));
            """;

    @TempDir
    Path scripts;

    private H2Database database;

    @BeforeEach
    void freshDatabase() throws SQLException {
        database = H2Database.create(SCHEMA);
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        database.close();
    }

    private Path script(String name, String sql) throws IOException {
        return Files.writeString(scripts.resolve(name), sql);
    }

    private CliRun fill(String... args) {
        return CliRun.fill(database.url(), args);
    }

    @Test
    void fillsEveryTableWithTheDefaultRowsAndKeepsForeignKeysValid() throws SQLException {
        CliRun run = fill("--rows", "25", "--seed", "1", "--exclude", "audit");

        assertEquals(0, run.code(), run.err());
        assertEquals("", run.err());
        assertEquals(25, database.count("customers"));
        assertEquals(25, database.count("orders"));
        assertEquals(25, database.count("order_stats"));
        assertEquals(0, database.scalar("select count(*) from orders o left join customers c on c.id = o.customer_id "
                + "where c.id is null"), "every order must reference an existing customer");
    }

    @Test
    void rowsDefaultsToOneHundred() throws SQLException {
        CliRun run = fill("--exclude", "audit,order_stats,orders");

        assertEquals(0, run.code(), run.err());
        assertEquals(100, database.count("customers"));
    }

    @Test
    void tableRowsOverrideTheDefaultRepeatedOrCommaSeparated() throws SQLException {
        CliRun run = fill("--rows", "10", "--exclude", "audit,order_stats",
                "--table-rows", "customers=30,orders=45", "--table-rows", "CUSTOMERS=30");

        // the same table twice (in different case) is a conflict, however it is spelled
        assertEquals(2, run.code(), run.err());
        assertTrue(run.err().contains("more than once"), run.err());
        assertEquals(0, database.count("customers"));

        CliRun valid = fill("--rows", "10", "--exclude", "audit,order_stats",
                "--table-rows", "customers=30", "--table-rows", "ORDERS=45");
        assertEquals(0, valid.code(), valid.err());
        assertEquals(30, database.count("customers"));
        assertEquals(45, database.count("orders"));
    }

    @Test
    void anUnknownTableRowsNameIsAUsageErrorAndNothingIsWritten() throws SQLException {
        CliRun run = fill("--table-rows", "custmers=5");

        assertEquals(2, run.code());
        assertTrue(run.err().startsWith("bloviate: --table-rows names no table"), run.err());
        assertTrue(run.err().contains("custmers"), run.err());
        assertTrue(run.err().contains("CUSTOMERS"), "the message lists the real tables: " + run.err());
        assertEquals(0, database.count("customers"));
    }

    @Test
    void includeAndExcludeNarrowTheTablesFilled() throws SQLException {
        CliRun run = fill("--rows", "12", "--include", "cust*", "--include", "orders", "--exclude", "order_stats");

        assertEquals(0, run.code(), run.err());
        assertEquals(12, database.count("customers"));
        assertEquals(12, database.count("orders"));
        assertEquals(0, database.count("order_stats"));
        assertEquals(0, database.count("audit"));
    }

    @Test
    void excludeAloneLeavesADerivedTableEmpty() throws SQLException {
        CliRun run = fill("--rows", "12", "--exclude", "order_stats", "--exclude", "audit");

        assertEquals(0, run.code(), run.err());
        assertEquals(12, database.count("orders"));
        assertEquals(0, database.count("order_stats"));
    }

    @Test
    void beforeAndAfterScriptsRunInOrderAroundTheFill() throws SQLException, IOException {
        Path before1 = script("b1.sql", "insert into audit (note) values ('before-1');");
        Path before2 = script("b2.sql", "insert into audit (note) values ('before-2');");
        Path after1 = script("a1.sql", """
                insert into audit (note) values ('after-1');
                insert into order_stats
                select c.region, sum(o.amount), count(*) from orders o join customers c on c.id = o.customer_id
                group by c.region;
                """);
        Path after2 = script("a2.sql", "insert into audit (note) values ('after-2');");

        CliRun run = fill("--rows", "40", "--seed", "3", "--exclude", "audit,order_stats",
                "--before", before1.toString(), "--before", before2.toString(),
                "--after", after1.toString(), "--after", after2.toString());

        assertEquals(0, run.code(), run.err());
        assertEquals(List.of("before-1", "before-2", "after-1", "after-2"),
                database.rows("select note from audit order by seq"));
        // the rollup was computed from the rows the fill generated, so it cannot disagree with them
        assertEquals(database.scalar("select sum(amount) from orders"), database.scalar("select sum(total) from order_stats"));
        assertEquals(40, database.scalar("select sum(n) from order_stats"));
    }

    @Test
    void aFailingAfterScriptFailsTheRunAndTheScriptsAfterItDoNotRun() throws SQLException, IOException {
        Path first = script("ok.sql", "insert into audit (note) values ('first');");
        Path failing = script("boom.sql", "select 1 / 0;");
        Path third = script("never.sql", "insert into audit (note) values ('third');");

        CliRun run = fill("--rows", "10", "--exclude", "audit,order_stats",
                "--after", first.toString(), "--after", failing.toString(), "--after", third.toString());

        assertEquals(1, run.code());
        assertTrue(run.err().startsWith("bloviate: fill failed:"), run.err());
        assertTrue(run.err().contains("boom.sql"), "the failing script is named: " + run.err());
        assertEquals(List.of("first"), database.rows("select note from audit order by seq"),
                "the script after the failing one must not have run");
        assertEquals(10, database.count("customers"), "tables filled before the failure stay filled");
    }

    @Test
    void aFailingBeforeScriptStopsBeforeAnythingIsFilled() throws SQLException, IOException {
        Path failing = script("bad.sql", "insert into no_such_table values (1);");

        CliRun run = fill("--rows", "10", "--before", failing.toString());

        assertEquals(1, run.code());
        assertTrue(run.err().contains("bad.sql"), run.err());
        assertEquals(0, database.count("customers"));
    }

    @Test
    void aFailedInsertNamesTheTableAndKeepsTheDriversMessage() throws SQLException {
        // a fill is not idempotent: the second run collides on the primary key
        assertEquals(0, fill("--rows", "5", "--exclude", "audit,order_stats").code());

        CliRun again = fill("--rows", "5", "--exclude", "audit,order_stats");

        assertEquals(1, again.code());
        assertTrue(again.err().startsWith("bloviate: fill failed: failed to fill table ["), again.err());
        assertTrue(again.err().toLowerCase().contains("unique index or primary key violation"),
                "the driver's own message is kept: " + again.err());
        assertEquals(1, again.err().lines().count(), "one line without -v: " + again.err());
    }

    @Test
    void verboseAddsTheStackTrace() throws SQLException {
        assertEquals(0, fill("--rows", "5", "--exclude", "audit,order_stats").code());

        CliRun again = fill("--rows", "5", "--exclude", "audit,order_stats", "-v");

        assertEquals(1, again.code());
        assertTrue(again.err().contains("\tat "), "a stack trace under -v: " + again.err());
    }

    @Test
    void threadsFillTablesConcurrentlyThroughTheDriverManagerDataSource() throws SQLException {
        CliRun run = fill("--rows", "60", "--seed", "9", "--threads", "3", "--exclude", "audit");

        assertEquals(0, run.code(), run.err());
        assertEquals(60, database.count("customers"));
        assertEquals(60, database.count("orders"));
        assertEquals(60, database.count("order_stats"));
        assertEquals(0, database.scalar("select count(*) from orders o left join customers c on c.id = o.customer_id "
                + "where c.id is null"));
    }

    @Test
    void parallelAndSequentialFillsProduceTheSameRows() throws SQLException {
        assertEquals(0, fill("--rows", "30", "--seed", "5", "--exclude", "audit").code());
        List<String> sequential = database.rows("select * from orders order by id");
        database.execute("delete from orders; delete from customers; delete from order_stats;");

        assertEquals(0, fill("--rows", "30", "--seed", "5", "--exclude", "audit", "--threads", "3").code());

        assertEquals(sequential, database.rows("select * from orders order by id"));
    }

    @Test
    void everyCommitModeFills() throws SQLException {
        for (String[] mode : new String[][]{
                {"--commit", "connection-default"},
                {"--commit", "per-table"},
                {"--commit", "every-n-batches", "--commit-batches", "2", "--batch-size", "4"},
                {"--commit", "PER-TABLE", "--threads", "2"}}) {
            database.execute("delete from orders; delete from customers;");
            String[] args = new String[mode.length + 4];
            System.arraycopy(mode, 0, args, 0, mode.length);
            args[mode.length] = "--rows";
            args[mode.length + 1] = "30";
            args[mode.length + 2] = "--exclude";
            args[mode.length + 3] = "audit,order_stats";

            CliRun run = fill(args);

            assertEquals(0, run.code(), String.join(" ", mode) + ": " + run.err());
            assertEquals(30, database.count("orders"), String.join(" ", mode));
        }
    }

    @Test
    void bulkLoadOrderedFillsAndUnorderedFallsBackWhereTheDatabaseCannotDoIt() throws SQLException {
        assertEquals(0, fill("--rows", "10", "--bulk-load", "ordered", "--exclude", "audit").code());
        database.execute("delete from orders; delete from customers; delete from order_stats;");

        // H2 cannot disable constraints, so the core warns and uses the ordered parallel path
        CliRun run = fill("--rows", "10", "--bulk-load", "unordered", "--threads", "2", "--exclude", "audit");

        assertEquals(0, run.code(), run.err());
        assertEquals(10, database.count("orders"));
    }

    @Test
    void supportCanBeChosenInsteadOfDetected() throws SQLException {
        assertEquals(0, fill("--rows", "5", "--support", "H2", "--exclude", "audit").code());
        database.execute("delete from orders; delete from customers; delete from order_stats;");

        assertEquals(0, fill("--rows", "5", "--support", "default", "--exclude", "audit").code());
        assertEquals(5, database.count("orders"));
    }

    @Test
    void schemaSelectsWhereTheTablesAreFilled() throws SQLException {
        database.execute("""
                create schema other;
                create table other.widgets (id int primary key, label varchar(20));
                """);

        CliRun run = fill("--schema", "OTHER", "--rows", "7");

        assertEquals(0, run.code(), run.err());
        assertEquals(7, database.count("other.widgets"));
        assertEquals(0, database.count("customers"), "the default schema must be untouched");
    }

    @Test
    void aSchemaWithAnUnderscoreDoesNotSeeTheOtherSchemasTables() throws SQLException {
        // getTables takes the schema as a LIKE pattern, so TENANT_1 would also match TENANTX1 and
        // --table-rows would accept the other schema's table as if it were one of this schema's
        database.execute("""
                create schema tenant_1;
                create schema tenantx1;
                create table tenant_1.widgets (id int primary key, label varchar(20));
                create table tenantx1.gadgets (id int primary key);
                """);

        CliRun run = fill("--schema", "TENANT_1", "--rows", "7", "--table-rows", "gadgets=3");

        assertEquals(2, run.code(), run.err());
        assertTrue(run.err().startsWith("bloviate: --table-rows names no table"), run.err());
        assertEquals(0, database.count("tenantx1.gadgets"));

        CliRun valid = fill("--schema", "TENANT_1", "--rows", "7");

        assertEquals(0, valid.code(), valid.err());
        assertEquals(7, database.count("tenant_1.widgets"));
        assertEquals(0, database.count("tenantx1.gadgets"), "the other schema must be untouched");
    }

    @Test
    void aSchemaThatDoesNotExistIsAFillFailure() {
        CliRun run = fill("--schema", "NOPE");

        assertEquals(1, run.code());
        assertTrue(run.err().startsWith("bloviate: fill failed:"), run.err());
    }

    @Test
    void theSameSeedIsReproducibleAndADifferentSeedDiffers() throws SQLException {
        // the database name is part of a column's seed, so both fills use a database of the same name
        String name = "cli_seed_repro";
        List<String> first;
        List<String> second;
        List<String> other;
        try (H2Database one = H2Database.named(name, null, null, SCHEMA)) {
            assertEquals(0, CliRun.fill(one.url(), "--rows", "20", "--seed", "42", "--exclude", "audit").code());
            first = one.rows("select * from customers order by id");
        }
        try (H2Database two = H2Database.named(name, null, null, SCHEMA)) {
            assertEquals(0, CliRun.fill(two.url(), "--rows", "20", "--seed", "42", "--exclude", "audit").code());
            second = two.rows("select * from customers order by id");
        }
        try (H2Database three = H2Database.named(name, null, null, SCHEMA)) {
            assertEquals(0, CliRun.fill(three.url(), "--rows", "20", "--seed", "43", "--exclude", "audit").code());
            other = three.rows("select * from customers order by id");
        }

        assertEquals(20, first.size());
        assertEquals(first, second, "the same seed must give identical data");
        assertNotEquals(first, other, "a different seed must give different data");
    }

    // ---- usage errors: exit 2, nothing connected to or written ----

    @Test
    void anUnknownFlagIsAUsageError() {
        CliRun run = fill("--bogus");

        assertEquals(2, run.code());
        assertTrue(run.err().startsWith("bloviate: Unknown option: '--bogus'"), run.err());
        assertTrue(run.err().contains("--help"), run.err());
    }

    @Test
    void aMissingUrlIsAUsageError() {
        CliRun run = CliRun.run("fill", "--rows", "5");

        assertEquals(2, run.code());
        assertEquals("bloviate: missing required option '--url' (the JDBC URL of the database to fill)\n",
                run.err().replace("\r\n", "\n"));
    }

    @Test
    void anIncludePatternThatMatchesNothingIsAUsageError() throws SQLException {
        CliRun run = fill("--include", "nothing_like_this*");

        assertEquals(2, run.code());
        assertTrue(run.err().startsWith("bloviate: includeTables pattern(s)"), run.err());
        assertTrue(run.err().contains("nothing_like_this*"), run.err());
        assertEquals(0, database.count("customers"));
    }

    @Test
    void excludingATableASelectedTableReferencesIsAUsageErrorAndWritesNothing() throws SQLException {
        CliRun run = fill("--exclude", "customers");

        assertEquals(2, run.code());
        assertTrue(run.err().contains("[customers]") || run.err().toLowerCase().contains("customers"), run.err());
        assertEquals(0, database.count("orders"));
        assertEquals(0, database.count("customers"));
    }

    @Test
    void contradictoryOrOutOfRangeSettingsAreUsageErrors() {
        assertUsageError("--commit-batches only applies", fill("--commit-batches", "3"));
        assertUsageError("--commit every-n-batches needs --commit-batches", fill("--commit", "every-n-batches"));
        assertUsageError("--commit-batches must be >= 1", fill("--commit", "every-n-batches", "--commit-batches", "0"));
        assertUsageError("--bulk-load unordered needs --threads greater than 1", fill("--bulk-load", "unordered"));
        assertUsageError("--rows must be >= 0", fill("--rows", "-1"));
        assertUsageError("--batch-size must be >= 1", fill("--batch-size", "0"));
        assertUsageError("--threads must be >= 1", fill("--threads", "0"));
        assertUsageError("--table-rows customers must be >= 0", fill("--table-rows", "customers=-4"));
        assertUsageError("--include pattern must not be blank", fill("--include", " "));
        assertUsageError("--before script is not a readable file", fill("--before", "/no/such/script.sql"));
        assertUsageError("--after script is not a readable file", fill("--after", "/no/such/script.sql"));
    }

    @Test
    void badValuesAreRejectedByTheParser() {
        assertUsageError("Invalid value for option '--commit'", fill("--commit", "sometimes"));
        assertUsageError("expected one of [connection-default, per-table, every-n-batches]", fill("--commit", "sometimes"));
        assertUsageError("Invalid value for option '--bulk-load'", fill("--bulk-load", "sideways"));
        assertUsageError("Invalid value for option '--support'", fill("--support", "oracle"));
        assertUsageError("Invalid value for option '--seed'", fill("--seed", "abc"));
        assertUsageError("mutually exclusive", fill("-v", "-q"));
    }

    private static void assertUsageError(String expectedMessage, CliRun run) {
        assertEquals(2, run.code(), run.err());
        assertTrue(run.err().contains(expectedMessage), "expected [" + expectedMessage + "] in: " + run.err());
        assertTrue(run.err().startsWith("bloviate: "), run.err());
    }

    // ---- connection errors: exit 3 ----

    @Test
    void aUrlNoDriverAcceptsIsAConnectionError() {
        CliRun run = CliRun.fill("jdbc:nosuchdb://localhost/x");

        assertEquals(3, run.code());
        assertTrue(run.err().startsWith("bloviate: cannot connect to jdbc:nosuchdb://localhost/x: "), run.err());
        assertTrue(run.err().contains("No suitable driver"), run.err());
    }

    @Test
    void anUnreachableServerIsAConnectionError() {
        // nothing listens on port 1; the timeout keeps a filtered port from stalling the test
        CliRun run = CliRun.fill("jdbc:postgresql://127.0.0.1:1/none?connectTimeout=3");

        assertEquals(3, run.code());
        assertTrue(run.err().startsWith("bloviate: cannot connect to jdbc:postgresql://127.0.0.1:1/none"), run.err());
    }

    @Test
    void aDatabaseThatDoesNotExistIsAConnectionError() {
        CliRun run = CliRun.fill("jdbc:h2:mem:cli_never_created;IFEXISTS=TRUE");

        assertEquals(3, run.code());
        assertTrue(run.err().startsWith("bloviate: cannot connect to "), run.err());
    }
}
