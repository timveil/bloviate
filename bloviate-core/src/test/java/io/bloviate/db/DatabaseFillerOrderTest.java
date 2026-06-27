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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseFiller}'s dependency-ordering logic, exercised without
 * a live database connection.
 */
class DatabaseFillerOrderTest {

    private static final DatabaseFiller FILLER = new DatabaseFiller.Builder(null, null).build();

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
    void independentTablesAreAllIncluded() {
        Database database = new Database("test", "1", null, null, List.of(parentless("a"), parentless("b")));

        assertEquals(2, FILLER.fillOrder(database).size());
    }

    @Test
    void parentIsFilledBeforeChild() {
        Table parent = parentless("parent");
        Table child = childOf("child", parent);
        Database database = new Database("test", "1", null, null, List.of(child, parent));

        List<Table> order = FILLER.fillOrder(database);

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

        List<Table> order = FILLER.fillOrder(database);

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

        List<Table> order = FILLER.fillOrder(database);

        assertEquals(4, order.size());
        assertTrue(indexOf(order, "d") < indexOf(order, "b"));
        assertTrue(indexOf(order, "d") < indexOf(order, "c"));
        assertTrue(indexOf(order, "b") < indexOf(order, "a"));
        assertTrue(indexOf(order, "c") < indexOf(order, "a"));
    }
}
