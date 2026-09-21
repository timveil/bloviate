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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Resolves, once for a whole database, how each foreign-key column must be generated so that every
 * value it produces exists in the key it references.
 *
 * <p>The engine never reads a parent's rows back: a foreign-key column is made to <em>replay</em> the
 * key it points at, by generating from the same seed at the same row index. This class works out, for
 * every column, which seed that is and how far the replay may run.
 *
 * <h2>Which key a foreign key references</h2>
 *
 * The edges come from the columns the constraint actually names ({@code PKCOLUMN_NAME}), so a key to
 * a {@code UNIQUE} target, or to primary-key columns in a different order, resolves to the columns it
 * really references rather than to the parent's primary key by position.
 *
 * <h2>Columns shared by several foreign keys</h2>
 *
 * A column may appear in more than one foreign key &mdash; the tenant pattern, where {@code tenant_id}
 * is both a column of a composite key into the parent and a key into the tenant table, and the general
 * case of one column pointing at two unrelated keys. Its value has to exist in <em>every</em> key it
 * references, which only holds if those keys carry the same values.
 *
 * <p>So the rule is structural rather than per-key: columns linked by a foreign key (in either
 * direction) form one class, and the whole class generates from a single seed &mdash; its
 * <em>seed source</em>. Two keys that a shared column ties together therefore produce identical
 * values row for row, and the shared column matches both. The seed source is the class's referenced
 * end: the lowest, in catalog/schema/table/column order, of the columns that reference nothing
 * themselves, falling back to the lowest of the whole class when a cycle leaves none. That is the
 * same column the old chain-following resolver picked for an ordinary parent/child chain, so a schema
 * with no shared key column generates exactly as before.
 *
 * <p>This does mean a schema that points one column at two unrelated keys makes those two keys equal,
 * column for column. That is inherent: a value cannot be in both key spaces unless the key spaces
 * overlap, and equality is the only overlap that is reproducible without reading rows back.
 *
 * <h2>How far the replay may run</h2>
 *
 * {@link #wrapChain(Column)} is the tables a column wraps through, nearest parent first. Row <em>i</em>
 * of a child reads row {@code i % parentRows} of its parent, which itself reads row
 * {@code (i % parentRows) % grandparentRows} of the grandparent, so a child larger than any of its
 * ancestors cycles through their keys rather than running past them.
 *
 * <p>Folding the counts in order matters: reducing each column by the smallest count in its chain
 * instead lets a composite key's columns land on <em>different</em> parent rows whenever the counts are
 * not multiples of each other, and the tuple then matches nothing. Because a composite key constrains a
 * whole tuple and a single-column key only constrains membership, the widest key a column belongs to is
 * the one that governs its chain.
 *
 * <h2>Keys the database generates</h2>
 *
 * A key the database generates ({@code serial}, {@code IDENTITY}) is left out of the insert entirely,
 * so no generator of ours ever produces its values and there is no seed to share. For those,
 * {@link #databaseGenerated(Column)} tells the caller to count instead: a freshly filled table is
 * assigned 1..N, which a sequence generator reproduces exactly.
 *
 * @since 3.10.0
 */
public final class ForeignKeyPlan {

    private static final Logger logger = LoggerFactory.getLogger(ForeignKeyPlan.class);

    /**
     * Total order over columns, used to pick a class's seed source. It has to be stable across runs,
     * platforms and JDKs (see the seed-reproducibility invariant in CONTRIBUTING.md), so it compares
     * the identifiers themselves rather than anything hash- or locale-dependent.
     */
    private static final Comparator<Column> IDENTIFIER_ORDER = Comparator
            .comparing((Column column) -> orEmpty(column.catalog()))
            .thenComparing(column -> orEmpty(column.schema()))
            .thenComparing(Column::tableName)
            .thenComparing(Column::name);

    /** Each foreign-key column to the key columns it references, in the order the driver reported them. */
    private final Map<Column, List<Column>> references;

    /** Each linked column to the column whose seed its whole class generates from. */
    private final Map<Column, Column> seedSources;

    /** Each foreign-key column to the levels it wraps through, nearest parent first. */
    private final Map<Column, List<List<String>>> wrapChains;

    /** Each foreign-key column to whether the key it references is generated by the database. */
    private final Set<Column> databaseGenerated;

    private ForeignKeyPlan(Map<Column, List<Column>> references, Map<Column, Column> seedSources,
                           Map<Column, List<List<String>>> wrapChains, Set<Column> databaseGenerated) {
        this.references = references;
        this.seedSources = seedSources;
        this.wrapChains = wrapChains;
        this.databaseGenerated = databaseGenerated;
    }

    /**
     * Builds the plan for a database.
     *
     * <p>This reads metadata only; it issues no queries. The engine builds one per fill, so callers
     * that need a single answer can build one ad hoc without arranging to share it.
     *
     * @param database the database whose foreign keys to resolve
     * @return the plan
     * @throws IllegalArgumentException if a foreign key references a table that is not among the
     *         tables being filled
     */
    public static ForeignKeyPlan of(Database database) {
        Objects.requireNonNull(database, "database must not be null");

        Map<Column, Column> governing = new LinkedHashMap<>();
        Map<Column, List<Column>> references = readReferences(database, governing);
        Map<Column, Column> seedSources = seedSources(references);

        return new ForeignKeyPlan(references, seedSources,
                wrapChains(references, governing), databaseGenerated(seedSources));
    }

    /**
     * The key column a foreign-key column ultimately references: the end of the chain, following the
     * first key of each column it passes through.
     *
     * <p>This describes the schema, not how the column is generated &mdash; use
     * {@link #seedSource(Column)} for that, which differs whenever a column is shared by several keys.
     *
     * @param column the column to resolve
     * @return the referenced key column, or null if the column is not part of any foreign key
     */
    public Column referencedRoot(Column column) {
        List<Column> direct = references.get(column);

        if (direct == null) {
            return null;
        }

        Set<Column> visited = new HashSet<>();
        visited.add(column);

        Column current = direct.getFirst();

        while (true) {
            if (!visited.add(current)) {
                // the chain closed on a column already on it; stop rather than follow it forever
                logger.warn("circular foreign-key reference detected at [{}.{}]; stopping key resolution",
                        current.tableName(), current.name());
                return current;
            }

            List<Column> onward = references.get(current);

            if (onward == null) {
                return current;
            }

            current = onward.getFirst();
        }
    }

    /**
     * The column whose seed this column's generator is built from.
     *
     * <p>Every column linked to it by a foreign key, in either direction, answers the same column, so
     * they all generate the same values and a foreign key matches the key it references. A column that
     * takes part in no foreign key is its own seed source, which is what it generated from before this
     * rule existed.
     *
     * @param column the column to resolve
     * @return the column to derive the seed from; never null
     */
    public Column seedSource(Column column) {
        return seedSources.getOrDefault(column, column);
    }

    /**
     * The tables a column wraps through, nearest parent first.
     *
     * <p>A row index is reduced by each table's row count in turn: row <em>i</em> of a child reads row
     * {@code i % parentRows} of its parent, which itself reads row {@code (i % parentRows) %
     * grandparentRows} of the grandparent, and so on. Folding rather than taking the smallest count is
     * what keeps a composite key's columns on one parent row: reduce each column independently and,
     * where the counts are not multiples of each other, they end up reading different rows and the
     * tuple matches nothing.
     *
     * @param column the column to resolve
     * @return one entry per level, nearest parent first; each is the tables to take the smallest row
     *         count over. Empty if the column is not part of any foreign key.
     */
    public List<List<String>> wrapChain(Column column) {
        return wrapChains.getOrDefault(column, List.of());
    }

    /**
     * Whether this column belongs to a class the database assigns rather than Bloviate.
     *
     * <p>A {@code serial} or {@code IDENTITY} key column is left out of the insert, so the database
     * assigns it: a table filled from empty is given 1..N, in insertion order. A foreign key to such a
     * key cannot share a seed with it &mdash; there is no seed &mdash; and must count instead. Since a
     * class carries one set of values, that applies to every column of the class, not only the ones
     * that reference the generated key directly.
     *
     * @param column the column to resolve
     * @return true if the column's class is database-generated
     */
    public boolean databaseGenerated(Column column) {
        return databaseGenerated.contains(column);
    }

    // -------------------------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------------------------

    private static Map<Column, List<Column>> readReferences(Database database, Map<Column, Column> governing) {
        // how wide the foreign key was that supplied each column's governing reference
        Map<Column, Integer> governingWidth = new HashMap<>();

        // LinkedHashMap and a list per column keep the driver's foreign-key order, which decides the
        // chain referencedRoot follows; hash order is JDK-dependent and would break reproducibility
        Map<Column, List<Column>> references = new LinkedHashMap<>();

        for (Table table : database.tables()) {

            List<ForeignKey> foreignKeys = table.foreignKeys();

            if (foreignKeys == null) {
                continue;
            }

            for (ForeignKey foreignKey : foreignKeys) {

                requireReferencedTableResolvable(database, table, foreignKey);

                Map<Integer, Column> referencedBySequence = new HashMap<>();
                for (KeyColumn referenced : foreignKey.primaryKey().keyColumns()) {
                    referencedBySequence.put(referenced.sequence(), referenced.column());
                }

                for (KeyColumn keyColumn : foreignKey.foreignKeyColumns()) {

                    Column referenced = referencedBySequence.get(keyColumn.sequence());

                    if (referenced == null) {
                        continue;
                    }

                    references.computeIfAbsent(keyColumn.column(), column -> new ArrayList<>()).add(referenced);

                    // A composite key constrains a whole tuple, so every one of its columns has to read
                    // the same row of the parent; a single-column key only constrains membership, which
                    // the wider key already delivers. So the widest key a column belongs to governs how
                    // far it wraps, and the first one reported wins a tie.
                    int width = foreignKey.foreignKeyColumns().size();
                    if (width > governingWidth.getOrDefault(keyColumn.column(), 0)) {
                        governingWidth.put(keyColumn.column(), width);
                        governing.put(keyColumn.column(), referenced);
                    }
                }
            }
        }

        return references;
    }

    /**
     * A key must name a table that is being filled. A key into another schema or catalog never does:
     * its key columns are deliberately not read, and the same-named table of <em>this</em> schema is a
     * different table, so resolving it by name would line the child up against the wrong parent. Both
     * are reported the same way, which is what {@link DatabaseFiller} does before a fill starts.
     */
    private static void requireReferencedTableResolvable(Database database, Table table, ForeignKey foreignKey) {
        String referencedTable = foreignKey.primaryKey().tableName();

        if (!foreignKey.referencesOtherSchema() && database.findTable(referencedTable).isPresent()) {
            return;
        }

        String columns = foreignKey.foreignKeyColumns().stream()
                .map(keyColumn -> "[" + keyColumn.column().name() + "]")
                .collect(Collectors.joining(", "));

        throw new IllegalArgumentException(String.format(
                "table [%s] column(s) %s references table [%s], which is not among the tables being filled; "
                        + "include it in the table selection",
                table.name(), columns, referencedTable));
    }

    /**
     * Groups the linked columns into classes (union-find over the foreign-key edges, read as
     * undirected) and picks each class's seed source.
     */
    private static Map<Column, Column> seedSources(Map<Column, List<Column>> references) {

        Map<Column, Column> parents = new HashMap<>();

        for (Map.Entry<Column, List<Column>> entry : references.entrySet()) {
            for (Column referenced : entry.getValue()) {
                union(parents, entry.getKey(), referenced);
            }
        }

        Map<Column, List<Column>> classes = new LinkedHashMap<>();
        for (Column column : parents.keySet()) {
            classes.computeIfAbsent(find(parents, column), root -> new ArrayList<>()).add(column);
        }

        Map<Column, Column> seedSources = new HashMap<>();

        for (List<Column> members : classes.values()) {
            // the referenced end of the class: a column that references nothing itself. A cycle has
            // none, and then the class's lowest column stands in so the choice is still deterministic.
            List<Column> unreferencing = members.stream().filter(member -> !references.containsKey(member)).toList();
            List<Column> candidates = unreferencing.isEmpty() ? members : unreferencing;

            Column seedSource = candidates.stream().min(IDENTIFIER_ORDER).orElseThrow();

            for (Column member : members) {
                seedSources.put(member, seedSource);
            }
        }

        return seedSources;
    }

    /**
     * The chain each column wraps through, following its governing reference upward.
     *
     * <p>Each level is the tables to take the <em>smallest</em> row count over. A level holds more than
     * one table when a column references several keys that do not imply one another &mdash; the value
     * must be in all of them, so the smallest bounds it. A reference that the governing one already
     * reaches is left out: it is implied rather than an extra constraint, and counting it would reduce
     * the index below the row the governing key needs.
     *
     * <p>A chain that closes on itself stops there, so a self-referencing key wraps through its own
     * table once and a cycle contributes each of its tables once rather than looping.
     */
    private static Map<Column, List<List<String>>> wrapChains(Map<Column, List<Column>> references,
                                                              Map<Column, Column> governing) {
        Map<Column, List<List<String>>> chains = new HashMap<>();

        for (Column column : governing.keySet()) {
            List<List<String>> chain = new ArrayList<>();
            Set<Column> visited = new HashSet<>();
            visited.add(column);

            Column current = column;

            while (true) {
                Column next = governing.get(current);

                if (next == null) {
                    break;
                }

                chain.add(levelTables(references, current, next));

                if (!visited.add(next)) {
                    break;
                }

                current = next;
            }

            chains.put(column, List.copyOf(chain));
        }

        return chains;
    }

    /** The tables bounding one level: the governing reference's, plus any sibling it does not imply. */
    private static List<String> levelTables(Map<Column, List<Column>> references, Column column, Column governing) {
        Set<String> tables = new TreeSet<>();
        tables.add(governing.tableName());

        Set<Column> implied = new HashSet<>(reachableFrom(references, governing));
        implied.add(governing);

        for (Column reference : references.getOrDefault(column, List.of())) {
            if (!implied.contains(reference)) {
                tables.add(reference.tableName());
            }
        }

        return List.copyOf(tables);
    }

    private static Set<Column> databaseGenerated(Map<Column, Column> seedSources) {
        Set<Column> generatedSeedSources = new HashSet<>();

        for (Map.Entry<Column, Column> entry : seedSources.entrySet()) {
            if (Boolean.TRUE.equals(entry.getKey().autoIncrement())) {
                generatedSeedSources.add(entry.getValue());
            }
        }

        Set<Column> generated = new HashSet<>();

        for (Map.Entry<Column, Column> entry : seedSources.entrySet()) {
            if (generatedSeedSources.contains(entry.getValue())) {
                generated.add(entry.getKey());
            }
        }

        return generated;
    }

    /** Every column reachable by following references from {@code column}, excluding it unless a cycle returns to it. */
    private static List<Column> reachableFrom(Map<Column, List<Column>> references, Column column) {
        List<Column> reached = new ArrayList<>();
        Set<Column> seen = new HashSet<>();
        Deque<Column> pending = new ArrayDeque<>(references.getOrDefault(column, List.of()));

        while (!pending.isEmpty()) {
            Column next = pending.poll();

            if (!seen.add(next)) {
                continue;
            }

            reached.add(next);
            pending.addAll(references.getOrDefault(next, List.of()));
        }

        return reached;
    }

    /** Union-find root of {@code column}, registering it and compressing the path it walked. */
    private static Column find(Map<Column, Column> parents, Column column) {
        Column root = column;

        while (true) {
            Column next = parents.get(root);
            if (next == null || next.equals(root)) {
                break;
            }
            root = next;
        }

        Column current = column;
        while (true) {
            Column next = parents.put(current, root);
            if (next == null || next.equals(current)) {
                break;
            }
            current = next;
        }

        return root;
    }

    private static void union(Map<Column, Column> parents, Column left, Column right) {
        Column leftRoot = find(parents, left);
        Column rightRoot = find(parents, right);

        if (leftRoot.equals(rightRoot)) {
            return;
        }

        // attach in identifier order, so the forest does not depend on the order the edges arrive in
        if (IDENTIFIER_ORDER.compare(leftRoot, rightRoot) <= 0) {
            parents.put(rightRoot, leftRoot);
        } else {
            parents.put(leftRoot, rightRoot);
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
