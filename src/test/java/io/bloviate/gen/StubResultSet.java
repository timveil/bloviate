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

package io.bloviate.gen;

import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.ResultSet;

/**
 * Lightweight, dependency-free fakes for {@link ResultSet} and {@link Array} backed by
 * {@link Proxy dynamic proxies}. Only the getter exercised by a generator's
 * {@code get(ResultSet, int)} needs to return a meaningful value; every other method
 * falls back to a type-appropriate default. Returning {@code null} from the configured
 * getter simulates a SQL {@code NULL} column.
 */
final class StubResultSet {

    private StubResultSet() {
    }

    /**
     * Builds a {@link ResultSet} that returns {@code value} from the named single-int-argument
     * getter (e.g. {@code "getTimestamp"}, {@code "getString"}, {@code "getArray"},
     * {@code "getObject"}).
     */
    static ResultSet returning(String getterName, Object value) {
        return (ResultSet) Proxy.newProxyInstance(
                StubResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals(getterName) && isSingleIntArg(args)) {
                        return value;
                    }
                    if (method.getName().equals("wasNull")) {
                        return value == null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    /**
     * Builds a {@link java.sql.Array} whose {@link Array#getArray()} returns {@code elements}.
     */
    static Array array(Object[] elements) {
        return (Array) Proxy.newProxyInstance(
                StubResultSet.class.getClassLoader(),
                new Class<?>[]{Array.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getArray") && (args == null || args.length == 0)) {
                        return elements;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static boolean isSingleIntArg(Object[] args) {
        return args != null && args.length == 1 && args[0] instanceof Integer;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        // void.class
        return null;
    }
}
