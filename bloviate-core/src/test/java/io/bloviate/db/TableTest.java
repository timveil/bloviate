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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableTest {

    private static Column column(String name, String schema, Boolean autoIncrement) {
        return new Column(name, "t", schema, null, JDBCType.INTEGER, 10, null, "int4", autoIncrement, true, null, 1);
    }

    @Test
    void insertStringWithoutQuoteStringEmitsBareIdentifiers() {
        Table table = new Table("orders", null,
                List.of(column("id", null, false), column("qty", null, false)), List.of());

        assertEquals("insert into orders (id,qty) values (?,?)", table.insertString());
    }

    @Test
    void noArgInsertStringStaysUnqualifiedWhenSchemaPresent() {
        // the legacy no-arg form must keep emitting the bare table name for compatibility,
        // even when column metadata carries a schema
        Table table = new Table("orders", null, List.of(column("id", "public", false)), List.of());

        assertEquals("insert into orders (id) values (?)", table.insertString());
    }

    @Test
    void insertStringSubstitutesValueExpressions() {
        Table table = new Table("events", null,
                List.of(column("id", null, false), column("payload", null, false)), List.of());

        assertEquals("insert into `events` (`id`,`payload`) values (?,PARSE_JSON(?))",
                table.insertString("`", List.of("?", "PARSE_JSON(?)")));
    }

    @Test
    void insertStringWithNullExpressionsMatchesThePlainForm() {
        // the overload is what the engine always calls now, so its no-expression behavior must be
        // byte-identical to the form it replaced
        Table table = new Table("orders", null,
                List.of(column("id", "public", false), column("qty", null, false)), List.of());

        assertEquals(table.insertString("\""), table.insertString("\"", null));
    }

    @Test
    void insertStringRejectsAnExpressionWithoutExactlyOnePlaceholder() {
        // one parameter is bound per column by position, so any other count shifts every later
        // column's value onto the wrong parameter -- silent corruption rather than an error
        Table table = new Table("events", null,
                List.of(column("id", null, false), column("payload", null, false)), List.of());

        for (String bad : List.of("PARSE_JSON('x')", "RANGE(?, ?)")) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> table.insertString("`", List.of("?", bad)));
            assertTrue(e.getMessage().contains("payload"), e.getMessage());
        }

        assertThrows(IllegalStateException.class,
                () -> table.insertString("`", Arrays.asList("?", null)));
    }

    @Test
    void insertStringRejectsAMismatchedExpressionCount() {
        Table table = new Table("events", null,
                List.of(column("id", null, false), column("payload", null, false)), List.of());

        assertThrows(IllegalArgumentException.class, () -> table.insertString("`", List.of("?")));
    }

    @Test
    void insertStringCountsExpressionsAgainstFilteredColumns() {
        // auto-increment columns are excluded from the INSERT, so expressions align with the
        // filtered list rather than every column
        Table table = new Table("orders", null,
                List.of(column("id", null, true), column("qty", null, false)), List.of());

        assertEquals("insert into `orders` (`qty`) values (CAST(? AS INT64))",
                table.insertString("`", List.of("CAST(? AS INT64)")));
    }

    @Test
    void insertStringQuotesAndSchemaQualifiesIdentifiers() {
        Table table = new Table("order", null,
                List.of(column("user", "public", false), column("desc", "public", false)), List.of());

        assertEquals("insert into \"public\".\"order\" (\"user\",\"desc\") values (?,?)",
                table.insertString("\""));
    }

    @Test
    void insertStringSupportsNonStandardQuoteStrings() {
        Table table = new Table("order", null, List.of(column("desc", null, false)), List.of());

        assertEquals("insert into `order` (`desc`) values (?)", table.insertString("`"));
    }

    @Test
    void insertStringDoublesEmbeddedQuoteCharacters() {
        Table table = new Table("we\"ird", null, List.of(column("col\"umn", null, false)), List.of());

        assertEquals("insert into \"we\"\"ird\" (\"col\"\"umn\") values (?)", table.insertString("\""));
    }

    @Test
    void insertStringTreatsBlankQuoteStringAsUnsupported() {
        // JDBC drivers report a single space when identifier quoting is unsupported
        Table table = new Table("orders", null, List.of(column("id", "public", false)), List.of());

        assertEquals("insert into public.orders (id) values (?)", table.insertString(" "));
    }

    @Test
    void insertStringExcludesAutoIncrementColumns() {
        Table table = new Table("orders", null,
                List.of(column("id", null, true), column("qty", null, false)), List.of());

        assertEquals("insert into orders (qty) values (?)", table.insertString(null));
    }
}
