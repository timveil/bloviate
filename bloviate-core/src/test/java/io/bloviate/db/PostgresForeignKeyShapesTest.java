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
import org.jgrapht.traverse.NotDirectedAcyclicGraphException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how the engine treats several foreign-key shapes against a real PostgreSQL (issue #614): a
 * self-referencing key, a mutual cycle, a key to an auto-generated primary key, and a child sized
 * larger than a parent it knows nothing about. One schema per scenario in
 * {@code create_fk_shapes.postgres.sql}.
 *
 * <p>See {@link PostgresPartitionedSchemaTest} for the convention on cases that fail today.
 *
 * <p>Observed on PostgreSQL 18 when #614 was written:
 * <ul>
 *   <li>mutual cycle, sequential path: {@code NotDirectedAcyclicGraphException: Graph is not a DAG};</li>
 *   <li>mutual cycle, parallel path: no exception, a warning, and both tables left empty;</li>
 *   <li>FK to a serial or identity primary key: {@code Key (author_id)=(1077167994) is not present in
 *       table "authors"} &mdash; the child is given random integers, not the generated 1..N;</li>
 *   <li>child larger than an unconfigured parent: the first 10 rows are valid (the default row count)
 *       and {@code Batch entry 10 ... violates foreign key constraint "children_parent_id_fkey"};</li>
 *   <li>self-referencing FK: fills, but every row's {@code manager_id} equals its own {@code id}.</li>
 * </ul>
 */
class PostgresForeignKeyShapesTest extends BaseDatabaseTestCase {

    private static final int ROWS = 20;

    private static PostgresSchemaFixture fixture;

    @BeforeAll
    static void startDatabase() {
        fixture = new PostgresSchemaFixture("create_fk_shapes.postgres.sql");
    }

    @AfterAll
    static void stopDatabase() {
        fixture.close();
    }

    @BeforeEach
    void emptySchemas() throws SQLException {
        for (String schema : List.of("self_ref", "fk_cycle", "fk_identity", "fk_identity_always", "fk_cardinality", "partial_fail")) {
            fixture.reset(schema);
        }
    }

    private static DatabaseConfiguration configuration(int batchSize, long rows, Set<TableConfiguration> tables) {
        return new DatabaseConfiguration(batchSize, rows, new PostgresSupport(), tables, 42L);
    }

    // ---------------------------------------------------------------------------------------------
    // self-referencing foreign key
    // ---------------------------------------------------------------------------------------------

    /** A self-referencing FK fills and satisfies its (enforced) constraint. */
    @Test
    void selfReferencingForeignKeyIsFilled() throws SQLException {
        fixture.fillSequential("self_ref", configuration(16, ROWS, null));

        assertEquals(ROWS, fixture.count("self_ref.employees"));
        fixture.assertForeignKeysEnforced("self_ref", 1);
    }

    /**
     * Pins how the constraint is satisfied today: the FK column is seeded from the table's own primary
     * key, so row <em>n</em> references itself and the "hierarchy" is {@value #ROWS} roots. That is valid
     * but degenerate. If a realistic hierarchy is ever generated, update this test deliberately.
     */
    @Test
    void selfReferencingForeignKeyPointsEveryRowAtItself() throws SQLException {
        fixture.fillSequential("self_ref", configuration(16, ROWS, null));

        assertEquals(ROWS, fixture.queryLong("select count(*) from self_ref.employees where manager_id = id"));
        assertEquals(0, fixture.queryLong("select count(*) from self_ref.employees where manager_id is null"));
    }

    // ---------------------------------------------------------------------------------------------
    // mutual foreign-key cycle (#618)
    // ---------------------------------------------------------------------------------------------

    /**
     * Pins the sequential path: a raw JGraphT exception with no table names. Replace this and the
     * parallel pin below with {@link #noPathLeavesCyclicTablesSilentlyUnfilled()} when #618 lands.
     */
    @Test
    void cycleOnTheSequentialPathThrowsARawGraphException() {
        Exception thrown = assertThrows(Exception.class,
                () -> fixture.fillSequential("fk_cycle", configuration(16, ROWS, null)));

        assertInstanceOf(NotDirectedAcyclicGraphException.class, thrown);
        assertEquals("Graph is not a DAG", thrown.getMessage());
    }

    /**
     * Pins the parallel path: the same schema completes <em>without an exception</em> and leaves both
     * tables empty (the only trace is a WARN log line). See the sequential pin above.
     */
    @Test
    void cycleOnTheParallelPathSilentlySkipsTheTables() throws SQLException {
        fixture.fillParallel("fk_cycle", configuration(16, ROWS, null), 4);

        assertEquals(0, fixture.count("fk_cycle.accounts"));
        assertEquals(0, fixture.count("fk_cycle.users"));
    }

    /**
     * Desired outcome for #618, per its acceptance criterion: no path silently leaves tables unfilled.
     * A path may fill the cycle (the FK from {@code accounts} is nullable) or fail with an error, but it
     * must not return normally with empty tables. This asserts that for both paths.
     */
    @Test
    @Disabled("#618: the ordered parallel path completes normally but skips cyclic tables")
    void noPathLeavesCyclicTablesSilentlyUnfilled() throws SQLException {
        assertFilledOrFailedLoudly(() -> fixture.fillSequential("fk_cycle", configuration(16, ROWS, null)));

        fixture.reset("fk_cycle");

        assertFilledOrFailedLoudly(() -> fixture.fillParallel("fk_cycle", configuration(16, ROWS, null), 4));
    }

    private static void assertFilledOrFailedLoudly(SqlAction fill) throws SQLException {
        try {
            fill.run();
        } catch (Exception failedLoudly) {
            return;
        }
        assertEquals(ROWS, fixture.count("fk_cycle.accounts"), "returned normally, so accounts must be filled");
        assertEquals(ROWS, fixture.count("fk_cycle.users"), "returned normally, so users must be filled");
        fixture.assertForeignKeysEnforced("fk_cycle", 2);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws Exception;
    }

    // ---------------------------------------------------------------------------------------------
    // foreign key to an auto-generated primary key (#617)
    // ---------------------------------------------------------------------------------------------

    /**
     * Desired outcome for #617 (which lists "FK to an auto-increment PK" as unverified): a child's FK
     * values are the identity values the parent was actually given. Today the parent's key column is
     * excluded from the insert (the database generates 1..N) while the child is filled with random
     * integers seeded from that column, so nothing matches.
     */
    @Test
    @Disabled("#617: a foreign key to a serial primary key is filled with random integers, not the generated values")
    void foreignKeyToSerialPrimaryKeyIsFilled() throws SQLException {
        fixture.fillSequential("fk_identity", configuration(16, ROWS, null));

        assertEquals(ROWS, fixture.count("fk_identity.authors"));
        assertEquals(ROWS, fixture.count("fk_identity.books"));
        fixture.assertForeignKeysEnforced("fk_identity", 1);
    }

    /** As above, for {@code GENERATED ALWAYS AS IDENTITY}. */
    @Test
    @Disabled("#617: a foreign key to an identity primary key is filled with random integers, not the generated values")
    void foreignKeyToIdentityPrimaryKeyIsFilled() throws SQLException {
        fixture.fillSequential("fk_identity_always", configuration(16, ROWS, null));

        assertEquals(ROWS, fixture.count("fk_identity_always.publishers"));
        assertEquals(ROWS, fixture.count("fk_identity_always.imprints"));
        fixture.assertForeignKeysEnforced("fk_identity_always", 1);
    }

    // ---------------------------------------------------------------------------------------------
    // child larger than its parent (#617)
    // ---------------------------------------------------------------------------------------------

    /**
     * Control: when the parent has a row count of its own, the child's foreign key wraps around inside
     * the parent's key space, so a child with more rows than its parent is valid.
     */
    @Test
    void childLargerThanAConfiguredParentWrapsAroundTheParentKeys() throws SQLException {
        fixture.fillSequential("fk_cardinality", configuration(16, 10,
                Set.of(new TableConfiguration("parents", 10), new TableConfiguration("children", 100))));

        assertEquals(10, fixture.count("fk_cardinality.parents"));
        assertEquals(100, fixture.count("fk_cardinality.children"));
        fixture.assertForeignKeysEnforced("fk_cardinality", 1);
    }

    /**
     * Desired outcome for #617: the wrap limit falls back to the default row count when the parent has
     * no {@code TableConfiguration}. Today the limit is only set for a configured parent, so the child
     * runs past the parent's key space (rows 0..9 are valid, row 10 is not).
     */
    @Test
    @Disabled("#617: the FK wrap limit is only set when the parent has a TableConfiguration, not from defaultRowCount")
    void childLargerThanAnUnconfiguredParentStaysWithinTheParentKeys() throws SQLException {
        fixture.fillSequential("fk_cardinality", configuration(16, 10, Set.of(new TableConfiguration("children", 100))));

        assertEquals(10, fixture.count("fk_cardinality.parents"));
        assertEquals(100, fixture.count("fk_cardinality.children"));
        fixture.assertForeignKeysEnforced("fk_cardinality", 1);
    }

    // ---------------------------------------------------------------------------------------------
    // failure semantics
    // ---------------------------------------------------------------------------------------------

    /**
     * A constraint violation fails fast, and under the default commit strategy (the caller's autocommit)
     * nothing is rolled back: the parent table is complete and the failing table keeps every batch that
     * succeeded before the bad one. Here the child has 100 rows against 10 parents and a batch size of
     * 10, so exactly the first batch is committed. A re-run therefore hits primary-key violations.
     */
    @Test
    void failureUnderTheDefaultCommitStrategyLeavesTheFailingTablePartiallyFilled() throws SQLException {
        SQLException failure = assertThrows(SQLException.class, () -> fixture.fillSequential("partial_fail",
                configuration(10, 10, Set.of(new TableConfiguration("children", 100)))));

        assertTrue(failure.getMessage().contains("violates foreign key constraint \"children_parent_id_fkey\""), failure.getMessage());
        assertEquals(10, fixture.count("partial_fail.parents"));
        assertEquals(10, fixture.count("partial_fail.children"));
    }

    /**
     * With {@link CommitStrategy#perTable()} the failing table is rolled back as a unit, but tables that
     * filled successfully earlier stay committed: there is no cross-table rollback.
     */
    @Test
    void failureUnderPerTableCommitRollsBackTheFailingTableButKeepsEarlierTables() throws SQLException {
        DatabaseConfiguration configuration = new DatabaseConfiguration(10, 10, new PostgresSupport(),
                Set.of(new TableConfiguration("children", 100)), 42L, CommitStrategy.perTable());

        assertThrows(SQLException.class, () -> fixture.fillSequential("partial_fail", configuration));

        assertEquals(10, fixture.count("partial_fail.parents"));
        assertEquals(0, fixture.count("partial_fail.children"));
    }
}
