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
import io.bloviate.gen.SequentialIntegerGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 *   <li>mutual cycle, sequential path: {@code NotDirectedAcyclicGraphException: Graph is not a DAG}
 *       &mdash; fixed in #618: both ordered paths now fail alike, naming the tables;</li>
 *   <li>mutual cycle, parallel path: no exception, a warning, and both tables left empty &mdash; fixed
 *       in #618;</li>
 *   <li>FK to a serial or identity primary key: {@code Key (author_id)=(1077167994) is not present in
 *       table "authors"} &mdash; the child was given random integers, not the generated 1..N; fixed in
 *       #617, which counts through the values the database assigns instead;</li>
 *   <li>child larger than an unconfigured parent: the first 10 rows were valid (the default row count)
 *       and {@code Batch entry 10 ... violates foreign key constraint "children_parent_id_fkey"};
 *       fixed in #617, which takes the wrap limit from the default row count too;</li>
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
     * Both ordered paths now reject the cycle the same way, naming the tables (#618). This replaces the
     * two pins that recorded the old split behaviour: a raw {@code NotDirectedAcyclicGraphException}
     * with no table names on the sequential path, and a normal return with both tables left empty on
     * the parallel one.
     */
    @Test
    void cycleOnEitherOrderedPathFailsNamingTheTables() {
        for (String path : List.of("sequential", "parallel")) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> {
                        if ("sequential".equals(path)) {
                            fixture.fillSequential("fk_cycle", configuration(16, ROWS, null));
                        } else {
                            fixture.fillParallel("fk_cycle", configuration(16, ROWS, null), 4);
                        }
                    },
                    path);

            assertTrue(thrown.getMessage().contains("accounts") && thrown.getMessage().contains("users"),
                    path + ": " + thrown.getMessage());
            assertTrue(thrown.getMessage().contains("Nothing was written"), path + ": " + thrown.getMessage());
        }
    }

    /**
     * Desired outcome for #618, per its acceptance criterion: no path silently leaves tables unfilled.
     * A path may fill the cycle (the FK from {@code accounts} is nullable) or fail with an error, but it
     * must not return normally with empty tables. This asserts that for both paths.
     */
    @Test
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
     * A child's FK values are the identity values the parent was actually given. The parent's key column
     * is excluded from the insert, so the database generates 1..N and there is no seed for the child to
     * share; since #617 the child counts through the same range instead of drawing random integers.
     */
    @Test
    void foreignKeyToSerialPrimaryKeyIsFilled() throws SQLException {
        fixture.fillSequential("fk_identity", configuration(16, ROWS, null));

        assertEquals(ROWS, fixture.count("fk_identity.authors"));
        assertEquals(ROWS, fixture.count("fk_identity.books"));
        fixture.assertForeignKeysEnforced("fk_identity", 1);
    }

    /** As above, for {@code GENERATED ALWAYS AS IDENTITY}. */
    @Test
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
     * The wrap limit falls back to the default row count when the parent has no
     * {@code TableConfiguration}. It used to be set only for a configured parent, so the child ran past
     * the parent's key space (rows 0..9 were valid, row 10 was not).
     */
    @Test
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
     * Gives {@code children} a primary key that repeats after ten values, so the eleventh row collides
     * with the first: rows 0..9 are unique and the batch of 10 commits, row 10 fails. The failure is a
     * property of the configured generator rather than of the data, so it lands on the same row every
     * run, on every database.
     *
     * <p>These two cases used to get their failure from the child's <em>foreign key</em> running past an
     * unconfigured parent's key space. That was the #617 bug, so with it fixed the fill succeeds and
     * proves nothing about committing; {@link #childLargerThanAnUnconfiguredParentStaysWithinTheParentKeys}
     * now pins that shape as working. What these tests are about — where a fill stops and what survives
     * it — needs only <em>some</em> deterministic mid-fill failure.
     */
    private static TableConfiguration childrenWithARepeatingPrimaryKey() {
        return new TableConfiguration("children", 100, Set.of(new ColumnConfiguration("id",
                random -> new SequentialIntegerGenerator.Builder(random).start(1).end(10).build())));
    }

    /**
     * A constraint violation fails fast, and under the default commit strategy (the caller's autocommit)
     * nothing is rolled back: the parent table is complete and the failing table keeps every batch that
     * succeeded before the bad one. With a batch size of 10 and a key that repeats at row 10, exactly
     * the first batch is committed. A re-run therefore hits primary-key violations.
     */
    @Test
    void failureUnderTheDefaultCommitStrategyLeavesTheFailingTablePartiallyFilled() throws SQLException {
        SQLException failure = assertThrows(SQLException.class, () -> fixture.fillSequential("partial_fail",
                configuration(10, 10, Set.of(childrenWithARepeatingPrimaryKey()))));

        assertTrue(failure.getMessage().contains("duplicate key value violates unique constraint")
                && failure.getMessage().contains("children_pkey"), failure.getMessage());
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
                Set.of(childrenWithARepeatingPrimaryKey()), 42L, CommitStrategy.perTable());

        assertThrows(SQLException.class, () -> fixture.fillSequential("partial_fail", configuration));

        assertEquals(10, fixture.count("partial_fail.parents"));
        assertEquals(0, fixture.count("partial_fail.children"));
    }
}
