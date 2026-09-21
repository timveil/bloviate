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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseFiller}'s dependency-ordering logic, exercised without
 * a live database connection.
 */
class DatabaseFillerOrderTest {

    private static Column id(String tableName) {
        return new Column("id", tableName, null, null, JDBCType.INTEGER, 10, null, "int4", false, false, null, 1);
    }

    private static Column fk(String tableName, String name) {
        return new Column(name, tableName, null, null, JDBCType.INTEGER, 10, null, "int4", false, true, null, 2);
    }

    private static Table parentless(String name) {
        Column id = id(name);
        return new Table(name, new PrimaryKey(name, List.of(new KeyColumn(1, id))), List.of(id), List.of());
    }

    /** A table whose single FK references {@code parent}'s primary key. */
    private static Table childOf(String name, Table parent) {
        Column id = id(name);
        Column fk = fk(name, parent.name() + "_id");
        ForeignKey foreignKey = new ForeignKey(List.of(new KeyColumn(1, fk)), parent.primaryKey());
        return new Table(name, new PrimaryKey(name, List.of(new KeyColumn(1, id))), List.of(id, fk), List.of(foreignKey));
    }

    private static int indexOf(List<Table> order, String tableName) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).name().equals(tableName)) {
                return i;
            }
        }
        throw new AssertionError("table not in fill order: " + tableName);
    }

    @Test
    void aForeignKeyToATableOutsideTheDatabaseFailsNamingChildColumnAndParentBeforeOrdering() {
        Table parent = parentless("parent");
        Table child = childOf("child", parent);
        Database database = new Database("test", "1", null, null, List.of(child, parentless("other")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DatabaseFiller.fillOrder(database));

        assertTrue(e.getMessage().contains("[child]"), e.getMessage());
        assertTrue(e.getMessage().contains("[parent_id]"), e.getMessage());
        assertTrue(e.getMessage().contains("[parent]"), e.getMessage());
        assertTrue(e.getMessage().contains("includeTables"), e.getMessage());
    }

    @Test
    void everyOffendingForeignKeyIsReportedAtOnce() {
        Table p1 = parentless("p1");
        Table p2 = parentless("p2");
        Database database = new Database("test", "1", null, null, List.of(childOf("c1", p1), childOf("c2", p2)));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DatabaseFiller.fillOrder(database));

        assertTrue(e.getMessage().contains("[c1]") && e.getMessage().contains("[c2]"), e.getMessage());
    }

    @Test
    void aSelfReferencingTableIsNotMissingItsParent() {
        Column id = id("tree");
        Column fk = fk("tree", "parent_id");
        PrimaryKey pk = new PrimaryKey("tree", List.of(new KeyColumn(1, id)));
        Table tree = new Table("tree", pk, List.of(id, fk), List.of(new ForeignKey(List.of(new KeyColumn(1, fk)), pk)));
        Database database = new Database("test", "1", null, null, List.of(tree));

        assertEquals(1, DatabaseFiller.fillOrder(database).size());
    }

    @Test
    void findTableIsCaseInsensitiveAndDoesNotThrow() {
        Database database = new Database("test", "1", null, null, List.of(parentless("Orders")));

        assertTrue(database.findTable("ORDERS").isPresent());
        assertTrue(database.findTable("missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> database.getTable("missing"));
    }

    @Test
    void independentTablesAreAllIncluded() {
        Database database = new Database("test", "1", null, null, List.of(parentless("a"), parentless("b")));

        assertEquals(2, DatabaseFiller.fillOrder(database).size());
    }

    @Test
    void parentIsFilledBeforeChild() {
        Table parent = parentless("parent");
        Table child = childOf("child", parent);
        Database database = new Database("test", "1", null, null, List.of(child, parent));

        List<Table> order = DatabaseFiller.fillOrder(database);

        assertEquals(2, order.size());
        assertTrue(indexOf(order, "parent") < indexOf(order, "child"),
                "parent must be filled before child: " + order.stream().map(Table::name).toList());
    }

    @Test
    void multiLevelChainIsOrderedRootFirst() {
        Table grandparent = parentless("grandparent");
        Table parent = childOf("parent", grandparent);
        Table child = childOf("child", parent);

        // deliberately supply them out of dependency order
        Database database = new Database("test", "1", null, null, List.of(child, parent, grandparent));

        List<Table> order = DatabaseFiller.fillOrder(database);

        assertEquals(3, order.size());
        assertTrue(indexOf(order, "grandparent") < indexOf(order, "parent"));
        assertTrue(indexOf(order, "parent") < indexOf(order, "child"));
    }

    @Test
    void diamondDependencyOrdersRootBeforeAllDependents() {
        // a -> b, a -> c, b -> d, c -> d  (d is the root referenced by both b and c)
        Table d = parentless("d");
        Table b = childOf("b", d);
        Table c = childOf("c", d);

        Column aId = id("a");
        Column aToB = fk("a", "b_id");
        Column aToC = fk("a", "c_id");
        ForeignKey toB = new ForeignKey(List.of(new KeyColumn(1, aToB)), b.primaryKey());
        ForeignKey toC = new ForeignKey(List.of(new KeyColumn(1, aToC)), c.primaryKey());
        Table a = new Table("a", new PrimaryKey("a", List.of(new KeyColumn(1, aId))),
                List.of(aId, aToB, aToC), List.of(toB, toC));

        Database database = new Database("test", "1", null, null, List.of(a, b, c, d));

        List<Table> order = DatabaseFiller.fillOrder(database);

        assertEquals(4, order.size());
        assertTrue(indexOf(order, "d") < indexOf(order, "b"));
        assertTrue(indexOf(order, "d") < indexOf(order, "c"));
        assertTrue(indexOf(order, "b") < indexOf(order, "a"));
        assertTrue(indexOf(order, "c") < indexOf(order, "a"));
    }

    // ---------------------------------------------------------------------------------------------
    // mutual foreign-key cycles (issue #618)
    // ---------------------------------------------------------------------------------------------

    /**
     * A set of tables that reference each other in a ring: {@code names[0] -> names[1] -> ... -> names[0]}.
     * Each table's foreign key has to name a {@link PrimaryKey} of the next table, so the primary keys
     * are built first and the tables wired to them afterwards.
     */
    private static List<Table> cycle(String... names) {
        List<PrimaryKey> primaryKeys = new ArrayList<>();
        for (String name : names) {
            primaryKeys.add(new PrimaryKey(name, List.of(new KeyColumn(1, id(name)))));
        }

        List<Table> tables = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            String next = names[(i + 1) % names.length];
            Column fk = fk(name, next + "_id");
            tables.add(new Table(name, primaryKeys.get(i), List.of(id(name), fk),
                    List.of(new ForeignKey(List.of(new KeyColumn(1, fk)), primaryKeys.get((i + 1) % names.length)))));
        }
        return tables;
    }

    @Test
    void twoTablesReferencingEachOtherFailNamingBoth() {
        Database database = new Database("test", "1", null, null, cycle("invoice", "payment"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DatabaseFiller.fillOrder(database));

        assertTrue(e.getMessage().contains("invoice") && e.getMessage().contains("payment"), e.getMessage());
        assertTrue(e.getMessage().contains("Nothing was written"), e.getMessage());
        // the message has to say what to do about it, not just that it happened
        assertTrue(e.getMessage().contains("unorderedBulk"), e.getMessage());
    }

    @Test
    void aLongerCycleIsReportedAsOneCycle() {
        Database database = new Database("test", "1", null, null, cycle("a", "b", "c"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DatabaseFiller.fillOrder(database));

        assertTrue(e.getMessage().contains("[a, b, c]"), "the whole ring belongs in one group: " + e.getMessage());
    }

    @Test
    void everyCycleIsReportedAtOnce() {
        List<Table> tables = new ArrayList<>(cycle("x1", "x2"));
        tables.addAll(cycle("y1", "y2"));
        Database database = new Database("test", "1", null, null, tables);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DatabaseFiller.fillOrder(database));

        assertTrue(e.getMessage().contains("[x1, x2]"), e.getMessage());
        assertTrue(e.getMessage().contains("[y1, y2]"), e.getMessage());
    }

    @Test
    void aCycleFailsEvenWhenOtherTablesCouldBeOrdered() {
        List<Table> tables = new ArrayList<>(cycle("left", "right"));
        Table standalone = parentless("standalone");
        tables.add(standalone);
        tables.add(childOf("dependent", standalone));
        Database database = new Database("test", "1", null, null, tables);

        // the level-parallel path used to fill the orderable tables and silently skip the cycle
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DatabaseFiller.fillOrder(database));

        assertTrue(e.getMessage().contains("[left, right]"), e.getMessage());
        // the tables that could have been ordered are not named: they are not the problem
        assertFalse(e.getMessage().contains("standalone"), e.getMessage());
    }

    /**
     * A self-reference orders rows inside one table, not tables against each other, so it is not a
     * cycle and must keep filling.
     */
    @Test
    void aSelfReferencingTableIsNotACycle() {
        Column id = id("tree");
        Column fk = fk("tree", "parent_id");
        PrimaryKey pk = new PrimaryKey("tree", List.of(new KeyColumn(1, id)));
        Table tree = new Table("tree", pk, List.of(id, fk), List.of(new ForeignKey(List.of(new KeyColumn(1, fk)), pk)));
        Database database = new Database("test", "1", null, null, List.of(tree, parentless("other")));

        assertEquals(2, DatabaseFiller.fillOrder(database).size());
    }

    @Test
    void anAcyclicGraphPassesTheCycleCheck() {
        Table parent = parentless("parent");
        Database database = new Database("test", "1", null, null, List.of(childOf("child", parent), parent));

        assertDoesNotThrow(() -> DatabaseFiller.requireAcyclic(DatabaseFiller.buildReversedDependencyGraph(database)));
    }
}
