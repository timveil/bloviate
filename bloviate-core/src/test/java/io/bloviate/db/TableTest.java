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
