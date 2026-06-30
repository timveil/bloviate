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

package io.bloviate.util;

import io.bloviate.db.Column;
import io.bloviate.db.Database;
import io.bloviate.db.ForeignKey;
import io.bloviate.db.KeyColumn;
import io.bloviate.db.PrimaryKey;
import io.bloviate.db.Table;
import org.junit.jupiter.api.Test;

import java.sql.JDBCType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link DatabaseUtils#getAssociatedPrimaryKeyColumn}, the recursive
 * foreign-key chain resolver that drives shared seeding between related columns.
 */
class AssociatedPrimaryKeyColumnTest {

    private static Column col(String name, String tableName) {
        return new Column(name, tableName, null, null, JDBCType.INTEGER, 10, null, "int4", false, false, null, 1);
    }

    private static PrimaryKey singleColumnPk(String tableName, Column column) {
        return new PrimaryKey(tableName, List.of(new KeyColumn(1, column)));
    }

    @Test
    void returnsNullWhenColumnHasNoForeignKeys() {
        Column id = col("id", "parent");
        Table parent = new Table("parent", singleColumnPk("parent", id), List.of(id), List.of());
        Database database = new Database("test", "1", null, null, List.of(parent));

        assertNull(DatabaseUtils.getAssociatedPrimaryKeyColumn(database, parent, id));
    }

    @Test
    void returnsNullForNonForeignKeyColumnOnTableThatHasForeignKeys() {
        Column parentId = col("id", "parent");
        Table parent = new Table("parent", singleColumnPk("parent", parentId), List.of(parentId), List.of());

        Column childFk = col("parent_id", "child");
        Column childOther = col("label", "child");
        ForeignKey fk = new ForeignKey(List.of(new KeyColumn(1, childFk)), singleColumnPk("parent", parentId));
        Table child = new Table("child", null, List.of(childFk, childOther), List.of(fk));

        Database database = new Database("test", "1", null, null, List.of(parent, child));

        // the non-FK column on a table that does have FKs must resolve to null
        assertNull(DatabaseUtils.getAssociatedPrimaryKeyColumn(database, child, childOther));
    }

    @Test
    void resolvesDirectForeignKeyToReferencedPrimaryKeyColumn() {
        Column parentId = col("id", "parent");
        Table parent = new Table("parent", singleColumnPk("parent", parentId), List.of(parentId), List.of());

        Column childFk = col("parent_id", "child");
        ForeignKey fk = new ForeignKey(List.of(new KeyColumn(1, childFk)), singleColumnPk("parent", parentId));
        Table child = new Table("child", null, List.of(childFk), List.of(fk));

        Database database = new Database("test", "1", null, null, List.of(parent, child));

        assertSame(parentId, DatabaseUtils.getAssociatedPrimaryKeyColumn(database, child, childFk));
    }

    @Test
    void followsMultiLevelChainToRootPrimaryKeyColumn() {
        // grandparent: pk gpId
        Column gpId = col("id", "grandparent");
        Table grandparent = new Table("grandparent", singleColumnPk("grandparent", gpId), List.of(gpId), List.of());

        // parent: pId is both its PK and a FK to grandparent.gpId
        Column pId = col("id", "parent");
        ForeignKey parentFk = new ForeignKey(List.of(new KeyColumn(1, pId)), singleColumnPk("grandparent", gpId));
        Table parent = new Table("parent", singleColumnPk("parent", pId), List.of(pId), List.of(parentFk));

        // child: cFk references parent.pId
        Column cFk = col("parent_id", "child");
        ForeignKey childFk = new ForeignKey(List.of(new KeyColumn(1, cFk)), singleColumnPk("parent", pId));
        Table child = new Table("child", null, List.of(cFk), List.of(childFk));

        Database database = new Database("test", "1", null, null, List.of(grandparent, parent, child));

        // should chase the chain all the way to the grandparent's PK column
        assertSame(gpId, DatabaseUtils.getAssociatedPrimaryKeyColumn(database, child, cFk));
    }

    @Test
    void matchesTheCorrectColumnInACompositeForeignKeyBySequence() {
        Column pkA = col("a", "parent");
        Column pkB = col("b", "parent");
        PrimaryKey parentPk = new PrimaryKey("parent", List.of(new KeyColumn(1, pkA), new KeyColumn(2, pkB)));
        Table parent = new Table("parent", parentPk, List.of(pkA, pkB), List.of());

        Column fkA = col("a", "child");
        Column fkB = col("b", "child");
        ForeignKey fk = new ForeignKey(List.of(new KeyColumn(1, fkA), new KeyColumn(2, fkB)), parentPk);
        Table child = new Table("child", null, List.of(fkA, fkB), List.of(fk));

        Database database = new Database("test", "1", null, null, List.of(parent, child));

        // sequence 2 on the child FK must resolve to the sequence-2 PK column, not sequence 1
        assertSame(pkB, DatabaseUtils.getAssociatedPrimaryKeyColumn(database, child, fkB));
        assertSame(pkA, DatabaseUtils.getAssociatedPrimaryKeyColumn(database, child, fkA));
    }

    @Test
    void terminatesOnCircularForeignKeyChainInsteadOfStackOverflow() {
        // A.id is the PK of A and a FK to B.id; B.id is the PK of B and a FK to A.id — a cycle A->B->A.
        Column aId = col("id", "a");
        Column bId = col("id", "b");

        ForeignKey aToB = new ForeignKey(List.of(new KeyColumn(1, aId)), singleColumnPk("b", bId));
        Table a = new Table("a", singleColumnPk("a", aId), List.of(aId), List.of(aToB));

        ForeignKey bToA = new ForeignKey(List.of(new KeyColumn(1, bId)), singleColumnPk("a", aId));
        Table b = new Table("b", singleColumnPk("b", bId), List.of(bId), List.of(bToA));

        Database database = new Database("test", "1", null, null, List.of(a, b));

        // the visited-set guard must break the cycle rather than recurse without bound
        Column resolved = assertDoesNotThrow(
                () -> DatabaseUtils.getAssociatedPrimaryKeyColumn(database, a, aId));
        assertSame(aId, resolved);
    }
}
