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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUrlsTest {

    @Test
    void hasParameterIsCaseInsensitiveAndValueAgnostic() {
        String url = "jdbc:postgresql://host:5432/db?reWriteBatchedInserts=true&stringtype=unspecified";
        assertTrue(JdbcUrls.hasParameter(url, "rewritebatchedinserts"));
        assertTrue(JdbcUrls.hasParameter(url, "stringtype"));
        assertFalse(JdbcUrls.hasParameter(url, "rewriteBatchedStatements"));
        assertFalse(JdbcUrls.hasParameter("jdbc:postgresql://host/db", "anything"));
        assertFalse(JdbcUrls.hasParameter(null, "anything"));
    }

    @Test
    void parameterEqualsComparesNameAndValueCaseInsensitively() {
        String url = "jdbc:postgresql://host/db?reWriteBatchedInserts=TRUE";
        assertTrue(JdbcUrls.parameterEquals(url, "rewritebatchedinserts", "true"));
        assertFalse(JdbcUrls.parameterEquals(url, "rewritebatchedinserts", "false"));
        assertFalse(JdbcUrls.parameterEquals("jdbc:mysql://host/db", "rewriteBatchedStatements", "true"));
    }

    @Test
    void appendParameterChoosesCorrectSeparator() {
        assertEquals("jdbc:postgresql://host/db?a=b",
                JdbcUrls.appendParameter("jdbc:postgresql://host/db", "a", "b"));
        assertEquals("jdbc:postgresql://host/db?x=1&a=b",
                JdbcUrls.appendParameter("jdbc:postgresql://host/db?x=1", "a", "b"));
    }

    @Test
    void withBatchRewriteAppendsWhenAbsent() {
        assertEquals("jdbc:postgresql://host/db?reWriteBatchedInserts=true",
                JdbcUrls.withBatchRewrite("jdbc:postgresql://host/db", "reWriteBatchedInserts"));
        assertEquals("jdbc:mysql://host/db?useSSL=false&rewriteBatchedStatements=true",
                JdbcUrls.withBatchRewrite("jdbc:mysql://host/db?useSSL=false", "rewriteBatchedStatements"));
    }

    @Test
    void withBatchRewriteIsNoOpWhenPresentOrUnsupported() {
        String already = "jdbc:postgresql://host/db?reWriteBatchedInserts=false";
        // preserves the caller's explicit value rather than overriding it
        assertEquals(already, JdbcUrls.withBatchRewrite(already, "reWriteBatchedInserts"));
        // null/blank parameter name (database has no such parameter) leaves the URL untouched
        assertEquals("jdbc:foo://host/db", JdbcUrls.withBatchRewrite("jdbc:foo://host/db", null));
        assertEquals("jdbc:foo://host/db", JdbcUrls.withBatchRewrite("jdbc:foo://host/db", "  "));
    }
}
