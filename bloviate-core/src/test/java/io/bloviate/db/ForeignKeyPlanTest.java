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

import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ForeignKeyPlan}, the rules that decide how a foreign-key column is generated
 * (issue #617). {@link ForeignKeyResolutionFillTest} drives the same rules through a real fill; these
 * pin the rules themselves, including the ones that exist for reproducibility rather than for
 * correctness of a single run.
 */
class ForeignKeyPlanTest {

    private static Column col(String name, String tableName) {
        return new Column(name, tableName, null, null, JDBCType.INTEGER, 10, null, "int4", false, false, null, 1);
    }

    private static Column generated(String name, String tableName) {
        return new Column(name, tableName, null, null, JDBCType.INTEGER, 10, null, "int4", true, false, null, 1);
    }

    private static PrimaryKey key(String tableName, Column... columns) {
        List<KeyColumn> keyColumns = new ArrayList<>();
        for (int i = 0; i < columns.length; i++) {
            keyColumns.add(new KeyColumn(i + 1, columns[i]));
        }
        return new PrimaryKey(tableName, keyColumns);
    }

    private static ForeignKey foreignKey(PrimaryKey referenced, Column... columns) {
        List<KeyColumn> keyColumns = new ArrayList<>();
        for (int i = 0; i < columns.length; i++) {
            keyColumns.add(new KeyColumn(i + 1, columns[i]));
        }
        return new ForeignKey(keyColumns, referenced);
    }

    private static Database database(Table... tables) {
        return new Database("test", "1", null, null, List.of(tables));
    }

    /** A column in no foreign key generates from itself, exactly as before any of this existed. */
    @Test
    void anUnlinkedColumnIsItsOwnSeedSource() {
        Column id = col("id", "parent");
        Column label = col("label", "parent");
        Table parent = new Table("parent", key("parent", id), List.of(id, label), List.of());

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(parent));

        assertSame(label, plan.seedSource(label));
        assertTrue(plan.wrapChain(label).isEmpty());
        assertFalse(plan.databaseGenerated(label));
    }

    /**
     * An ordinary chain resolves to the column at the far end &mdash; the same one the positional
     * resolver picked, which is why a schema without a shared key column generates unchanged.
     */
    @Test
    void anOrdinaryChainSharesTheSeedOfTheColumnAtItsEnd() {
        Column grandparentId = col("id", "grandparent");
        Table grandparent = new Table("grandparent", key("grandparent", grandparentId), List.of(grandparentId), List.of());

        Column parentId = col("id", "parent");
        Column parentRef = col("grandparent_id", "parent");
        Table parent = new Table("parent", key("parent", parentId), List.of(parentId, parentRef),
                List.of(foreignKey(key("grandparent", grandparentId), parentRef)));

        Column childRef = col("parent_id", "child");
        Table child = new Table("child", null, List.of(childRef),
                List.of(foreignKey(key("parent", parentId), childRef)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(grandparent, parent, child));

        assertSame(grandparentId, plan.seedSource(parentRef));
        assertSame(parentId, plan.seedSource(childRef));
        assertSame(parentId, plan.seedSource(parentId));
    }

    /**
     * The issue's shared-column case: one column referencing two unrelated keys. Both keys join its
     * class, so all three generate from one seed and the shared column satisfies each of them. No
     * per-key choice can do this &mdash; two independently seeded key spaces do not overlap.
     */
    @Test
    void aColumnInTwoForeignKeysPullsBothKeysIntoOneSeedClass() {
        Column regionId = col("id", "region");
        Table region = new Table("region", key("region", regionId), List.of(regionId), List.of());

        Column nationId = col("id", "nation");
        Table nation = new Table("nation", key("nation", nationId), List.of(nationId), List.of());

        Column bridgeRef = col("ref", "bridge");
        Table bridge = new Table("bridge", null, List.of(bridgeRef), List.of(
                foreignKey(key("region", regionId), bridgeRef),
                foreignKey(key("nation", nationId), bridgeRef)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(region, nation, bridge));

        Column seedSource = plan.seedSource(bridgeRef);
        assertSame(seedSource, plan.seedSource(regionId), "the referenced keys must share the shared column's seed");
        assertSame(seedSource, plan.seedSource(nationId));
        // the lowest of the class's unreferencing columns, so the choice does not depend on which key
        // the driver reported first
        assertSame(nationId, seedSource);

        // one level bounded by both keys: the value has to be in each, so the smaller bounds it
        assertEquals(List.of(List.of("nation", "region")), plan.wrapChain(bridgeRef));
    }

    /**
     * The seed source is chosen from the identifiers, not from traversal order, because the data a seed
     * produces must not depend on the order the driver happens to report tables in (CONTRIBUTING.md,
     * "Design Invariants").
     */
    @Test
    void theSeedSourceDoesNotDependOnTheOrderTablesAreReportedIn() {
        Column regionId = col("id", "region");
        Column nationId = col("id", "nation");
        Column bridgeRef = col("ref", "bridge");

        Table region = new Table("region", key("region", regionId), List.of(regionId), List.of());
        Table nation = new Table("nation", key("nation", nationId), List.of(nationId), List.of());
        Table bridge = new Table("bridge", null, List.of(bridgeRef), List.of(
                foreignKey(key("region", regionId), bridgeRef),
                foreignKey(key("nation", nationId), bridgeRef)));

        Table reversedBridge = new Table("bridge", null, List.of(bridgeRef), List.of(
                foreignKey(key("nation", nationId), bridgeRef),
                foreignKey(key("region", regionId), bridgeRef)));

        assertSame(ForeignKeyPlan.of(database(region, nation, bridge)).seedSource(bridgeRef),
                ForeignKeyPlan.of(database(bridge, nation, region)).seedSource(bridgeRef));
        assertSame(ForeignKeyPlan.of(database(region, nation, bridge)).seedSource(bridgeRef),
                ForeignKeyPlan.of(database(nation, region, reversedBridge)).seedSource(bridgeRef));
    }

    /**
     * A composite key pairs by sequence with the columns it names, so the tenant pattern
     * ({@code UNIQUE (tenant_id, id)} referenced by {@code (tenant_id, account_id)}) resolves to the
     * unique key's columns rather than to the parent's primary key by position.
     */
    @Test
    void aCompositeKeyPairsWithTheColumnsItNamesBySequence() {
        Column tenantId = col("id", "tenants");
        Table tenants = new Table("tenants", key("tenants", tenantId), List.of(tenantId), List.of());

        Column accountId = col("id", "accounts");
        Column accountTenant = col("tenant_id", "accounts");
        Table accounts = new Table("accounts", key("accounts", accountId), List.of(accountId, accountTenant),
                List.of(foreignKey(key("tenants", tenantId), accountTenant)));

        Column documentTenant = col("tenant_id", "documents");
        Column documentAccount = col("account_id", "documents");
        // the unique key (tenant_id, id), not the primary key (id)
        Table documents = new Table("documents", null, List.of(documentTenant, documentAccount), List.of(
                foreignKey(key("accounts", accountTenant, accountId), documentTenant, documentAccount),
                foreignKey(key("tenants", tenantId), documentTenant)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(tenants, accounts, documents));

        // sequence 2 resolves to the unique key's second column; positional matching against the
        // parent's primary key would have paired sequence 1 with accounts.id
        assertSame(accountId, plan.seedSource(documentAccount));
        assertSame(tenantId, plan.seedSource(documentTenant));
        // the composite key governs, and the standalone key to tenants is implied by it rather than an
        // extra bound, so the chain folds through accounts and then through accounts' own parent
        assertEquals(List.of(List.of("accounts"), List.of("tenants")), plan.wrapChain(documentTenant));
        assertEquals(List.of(List.of("accounts")), plan.wrapChain(documentAccount));
    }

    /** A key the database assigns has no seed to share, so the child is told to count instead. */
    @Test
    void aKeyToAGeneratedColumnIsReportedAsDatabaseGenerated() {
        Column authorId = generated("id", "authors");
        Table authors = new Table("authors", key("authors", authorId), List.of(authorId), List.of());

        Column bookAuthor = col("author_id", "books");
        Table books = new Table("books", null, List.of(bookAuthor),
                List.of(foreignKey(key("authors", authorId), bookAuthor)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(authors, books));

        assertTrue(plan.databaseGenerated(bookAuthor));
        // the generated column itself is reported too, harmlessly: it is left out of its own insert,
        // so nothing generates it
        assertTrue(plan.databaseGenerated(authorId));
    }

    /**
     * A column referencing one generated key and one ordinary key. Counting only the shared column
     * would leave the ordinary key on its random values with nothing to match, so the whole class
     * counts: the ordinary key is filled 1..N too.
     */
    @Test
    void aClassContainingAGeneratedKeyCountsAsAWhole() {
        Column generatedId = generated("id", "generated_parent");
        Table generatedParent = new Table("generated_parent", key("generated_parent", generatedId),
                List.of(generatedId), List.of());

        Column ordinaryCode = col("code", "ordinary_parent");
        Table ordinaryParent = new Table("ordinary_parent", key("ordinary_parent", ordinaryCode),
                List.of(ordinaryCode), List.of());

        Column bridgeRef = col("ref", "bridge");
        Table bridge = new Table("bridge", null, List.of(bridgeRef), List.of(
                foreignKey(key("generated_parent", generatedId), bridgeRef),
                foreignKey(key("ordinary_parent", ordinaryCode), bridgeRef)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(generatedParent, ordinaryParent, bridge));

        assertTrue(plan.databaseGenerated(bridgeRef));
        assertTrue(plan.databaseGenerated(ordinaryCode), "the ordinary key must count too, or it has nothing to match");
    }

    /**
     * The chain covers every level, not just the immediate parent: a grandchild reads a row of its
     * parent, which reads a row of the grandparent. The counts fold in that order rather than being
     * reduced to their minimum, which is what keeps a composite key's columns on one parent row.
     */
    @Test
    void theWrapChainFollowsEveryLevel() {
        Column grandparentId = col("id", "grandparent");
        Table grandparent = new Table("grandparent", key("grandparent", grandparentId), List.of(grandparentId), List.of());

        Column parentId = col("id", "parent");
        Table parent = new Table("parent", key("parent", parentId), List.of(parentId),
                List.of(foreignKey(key("grandparent", grandparentId), parentId)));

        Column childRef = col("parent_id", "child");
        Table child = new Table("child", null, List.of(childRef),
                List.of(foreignKey(key("parent", parentId), childRef)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(grandparent, parent, child));

        assertEquals(List.of(List.of("parent"), List.of("grandparent")), plan.wrapChain(childRef));
    }

    /** A self-referencing key is bounded by its own table, and still resolves to the key it names. */
    @Test
    void aSelfReferencingKeyIsBoundedByItsOwnTable() {
        Column id = col("id", "employees");
        Column managerId = col("manager_id", "employees");
        Table employees = new Table("employees", key("employees", id), List.of(id, managerId),
                List.of(foreignKey(key("employees", id), managerId)));

        ForeignKeyPlan plan = ForeignKeyPlan.of(database(employees));

        assertSame(id, plan.seedSource(managerId));
        assertEquals(List.of(List.of("employees")), plan.wrapChain(managerId));
    }
}
