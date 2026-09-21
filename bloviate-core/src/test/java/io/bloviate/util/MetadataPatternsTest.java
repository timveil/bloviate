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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link MetadataPatterns}: what each driver's escape character does to a literal name,
 * and the degraded behaviour for a driver that reports no escape at all.
 */
class MetadataPatternsTest {

    /** A {@link DatabaseMetaData} whose only live method is {@code getSearchStringEscape()}. */
    private static DatabaseMetaData metaDataEscaping(String escape) {
        return metaData(() -> escape);
    }

    private static DatabaseMetaData metaDataFailing() {
        return metaData(() -> {
            throw new SQLFeatureNotSupportedException("no escape here");
        });
    }

    private interface Escape {
        String get() throws SQLException;
    }

    private static DatabaseMetaData metaData(Escape escape) {
        return (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, args) -> {
                    if ("getSearchStringEscape".equals(method.getName())) {
                        return escape.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** The escape every mainstream driver (PostgreSQL, MySQL, H2) reports. */
    private static MetadataPatterns backslash() {
        return MetadataPatterns.forMetaData(metaDataEscaping("\\"));
    }

    @Test
    void escapesTheWildcards() {
        assertEquals("tenant\\_1", backslash().literal("tenant_1"));
        assertEquals("ten\\%ant", backslash().literal("ten%ant"));
        assertEquals("\\_\\%\\_", backslash().literal("_%_"));
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertEquals("back\\\\slash", backslash().literal("back\\slash"));
        assertEquals("\\\\\\_", backslash().literal("\\_"));
    }

    @Test
    void leavesANameWithoutWildcardsAlone() {
        assertEquals("orders", backslash().literal("orders"));
        assertEquals("", backslash().literal(""));
    }

    @Test
    void aMultiCharacterEscapeIsUsedWhole() {
        MetadataPatterns patterns = MetadataPatterns.forMetaData(metaDataEscaping("^^"));

        assertEquals("a^^_b", patterns.literal("a_b"));
        assertEquals("a^^^^b", patterns.literal("a^^b"));
    }

    @Test
    void aDriverWithoutAnEscapeLeavesNamesAlone() {
        // null, empty and blank all mean "this driver cannot escape"
        for (String escape : new String[]{null, "", " "}) {
            MetadataPatterns patterns = MetadataPatterns.forMetaData(metaDataEscaping(escape));

            assertSame(MetadataPatterns.NONE, patterns, "escape [" + escape + "]");
            assertEquals("tenant_1", patterns.literal("tenant_1"));
        }
    }

    @Test
    void aDriverThatThrowsLeavesNamesAloneRatherThanFailingTheRead() {
        MetadataPatterns patterns = MetadataPatterns.forMetaData(metaDataFailing());

        assertSame(MetadataPatterns.NONE, patterns);
        assertEquals("tenant_1", patterns.literal("tenant_1"));
    }

    @Test
    void nullMeansNoNarrowingAndStaysNull() {
        assertNull(backslash().literal(null));
        assertNull(MetadataPatterns.NONE.literal(null));
    }
}
