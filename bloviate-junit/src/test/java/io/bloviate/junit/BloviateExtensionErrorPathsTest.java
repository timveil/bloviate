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

package io.bloviate.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link BloviateExtension}'s source-resolution and validation branches without a database by
 * driving {@code beforeEach} with a minimal {@link ExtensionContext} stub against fixture classes that
 * misconfigure their {@link FillSource}. Each path is expected to fail before any fill is attempted.
 */
class BloviateExtensionErrorPathsTest {

    /** A tiny {@link ExtensionContext} that answers only the three methods the extension calls. */
    private static ExtensionContext context(Class<?> testClass, Object instance) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getTestMethod" -> Optional.empty();
            case "getRequiredTestClass" -> testClass;
            case "getRequiredTestInstance" -> {
                if (instance == null) {
                    throw new IllegalStateException("no test instance available");
                }
                yield instance;
            }
            case "toString" -> "stubExtensionContext";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(), new Class<?>[]{ExtensionContext.class}, handler);
    }

    private static String failureMessage(Class<?> testClass, Object instance) {
        BloviateExtension extension = new BloviateExtension();
        ExtensionConfigurationException thrown = assertThrows(ExtensionConfigurationException.class,
                () -> extension.beforeEach(context(testClass, instance)));
        return thrown.getMessage();
    }

    @FillDatabase
    static class NoSource {
    }

    @FillDatabase
    static class WrongType {
        @FillSource
        static String notASource = "nope";
    }

    @FillDatabase
    static class NullSource {
        @FillSource
        static Connection connection;
    }

    @FillDatabase
    static class MultipleAnnotated {
        @FillSource
        static Connection a;
        @FillSource
        static Connection b;
    }

    @FillDatabase
    static class MultipleCandidates {
        static DataSource dataSource;   // no @FillSource → ambiguous fallback
        static Connection connection;
    }

    @FillDatabase
    static class FallbackSingleNull {
        static String unrelated = "ignored"; // exercises the candidate predicate's negative branch
        static Connection only;              // the lone fallback candidate, but null
    }

    @FillDatabase
    static class InstanceNullSource {
        @FillSource
        Connection connection;   // non-static and null: read from the test instance, then rejected
    }

    @Test
    void noFillSourceAndNoCandidateFails() {
        assertTrue(failureMessage(NoSource.class, null).contains("no fill source"));
    }

    @Test
    void nonDataSourceOrConnectionFieldFails() {
        assertTrue(failureMessage(WrongType.class, null).contains("DataSource or java.sql.Connection"));
    }

    @Test
    void nullSourceFails() {
        assertTrue(failureMessage(NullSource.class, null).contains("is null"));
    }

    @Test
    void multipleAnnotatedFieldsFail() {
        assertTrue(failureMessage(MultipleAnnotated.class, null).contains("exactly one"));
    }

    @Test
    void multipleFallbackCandidatesFail() {
        assertTrue(failureMessage(MultipleCandidates.class, null).contains("multiple"));
    }

    @Test
    void fallbackToSingleCandidateThenNull() {
        assertTrue(failureMessage(FallbackSingleNull.class, null).contains("is null"));
    }

    @Test
    void nonStaticFieldIsReadFromTheTestInstance() {
        assertTrue(failureMessage(InstanceNullSource.class, new InstanceNullSource()).contains("is null"));
    }
}
