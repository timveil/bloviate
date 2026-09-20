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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link TableSelection}: glob matching and the include/exclude rules. */
class TableSelectionTest {

    private static final List<String> TABLES = List.of("customers", "orders", "order_items", "order_stats", "tmp_a", "tmp_b");

    private static TableSelection selection(List<String> includes, List<String> excludes) {
        return new TableSelection(includes, excludes);
    }

    @Test
    void globMatching() {
        String[][] cases = {
            {"orders", "orders", "true"},
            {"orders", "ORDERS", "true"},
            {"ORDERS", "orders", "true"},
            {"orders", "order", "false"},
            {"orders", "orders2", "false"},
            {"order*", "orders", "true"},
            {"order*", "order", "true"},
            {"order*", "my_orders", "false"},
            {"*_stats", "order_stats", "true"},
            {"*_stats", "order_stats_old", "false"},
            {"*", "anything", "true"},
            {"*", "", "true"},
            {"o?ders", "orders", "true"},
            {"o?ders", "oders", "false"},
            {"?", "a", "true"},
            {"?", "ab", "false"},
            {"a*b*c", "aXXbYYc", "true"},
            {"a*b*c", "aXXbYY", "false"},
            {"a*a*a", "aaa", "true"},
            {"a*a*a", "aa", "false"},
            {"*a*", "banana", "true"},
            {"*ana", "banana", "true"},
            {"*ano", "banana", "false"},
            {"tmp_?", "TMP_A", "true"},
            {"a.b", "a.b", "true"},
            {"a.b", "axb", "false"},
        };
        for (String[] c : cases) {
            assertEquals(Boolean.parseBoolean(c[2]), TableSelection.matches(c[0], c[1]), c[0] + " vs " + c[1]);
        }
    }

    @Test
    void regexMetacharactersAreLiteral() {
        assertTrue(TableSelection.matches("a+b", "a+b"));
        assertFalse(TableSelection.matches("a+b", "aab"));
        assertTrue(TableSelection.matches("(x)", "(x)"));
        assertFalse(TableSelection.matches("[a-z]", "b"));
    }

    @Test
    void noSelectionKeepsEveryTableAndIsNotConfigured() {
        assertFalse(TableSelection.ALL.isConfigured());
        assertSame(TABLES, TableSelection.ALL.select(TABLES));
        assertTrue(TableSelection.ALL.isSelected("whatever"));
    }

    @Test
    void includeKeepsOnlyMatchingTablesInDiscoveryOrder() {
        assertEquals(List.of("customers", "orders", "order_items", "order_stats"),
                selection(List.of("customers", "ORDER*"), List.of()).select(TABLES));
    }

    @Test
    void excludeDropsMatchingTables() {
        assertEquals(List.of("customers", "orders", "order_items"),
                selection(List.of(), List.of("order_stats", "TMP_*")).select(TABLES));
    }

    @Test
    void excludeIsAppliedAfterInclude() {
        assertEquals(List.of("orders", "order_items"),
                selection(List.of("order*"), List.of("*_stats")).select(TABLES));
    }

    @Test
    void anIncludePatternThatMatchesNothingFailsNamingIt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> selection(List.of("orders", "nope*", "typo"), List.of()).select(TABLES));

        assertTrue(e.getMessage().contains("nope*"), e.getMessage());
        assertTrue(e.getMessage().contains("typo"), e.getMessage());
        assertFalse(e.getMessage().contains("[orders,"), "a matching pattern must not be reported: " + e.getMessage());
    }

    @Test
    void anExcludePatternThatMatchesNothingIsOnlyAWarning() {
        assertEquals(List.of("customers", "orders", "order_items", "order_stats", "tmp_a", "tmp_b"),
                selection(List.of(), List.of("no_such_table")).select(TABLES));
    }

    @Test
    void aSelectionThatLeavesNothingFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> selection(List.of("orders"), List.of("orders")).select(TABLES));

        assertTrue(e.getMessage().contains("no table to fill"), e.getMessage());
    }

    @Test
    void theErrorListsAtMostSomeTableNames() {
        List<String> many = java.util.stream.IntStream.range(0, 50).mapToObj(i -> "t" + i).toList();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> selection(List.of("zzz"), List.of()).select(many));

        assertTrue(e.getMessage().contains("and 30 more"), e.getMessage());
    }

    @Test
    void isSelectedJudgesByNameAlone() {
        TableSelection selection = selection(List.of("order*"), List.of("*_stats"));

        assertTrue(selection.isSelected("orders"));
        assertFalse(selection.isSelected("order_stats"));
        assertFalse(selection.isSelected("customers"));
    }

    @Test
    void patternsAreValidated() {
        assertThrows(NullPointerException.class, () -> TableSelection.requirePattern(null));
        assertThrows(IllegalArgumentException.class, () -> TableSelection.requirePattern(" "));
        assertEquals("ok*", TableSelection.requirePattern("ok*"));
    }
}
