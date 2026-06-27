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

/**
 * Small helpers for inspecting and extending JDBC connection URLs.
 *
 * <p>Bloviate fills through a {@link java.sql.Connection}/{@link javax.sql.DataSource} it does not
 * own, so it cannot change the URL after the fact. These helpers let a caller who <em>does</em> build
 * the URL (or {@code DataSource}) add the driver's batch-rewrite parameter programmatically &mdash;
 * the optimization that is often the single biggest fill speedup. The matching parameter name comes
 * from {@code io.bloviate.ext.DatabaseSupport#batchRewriteUrlParameter()} (PostgreSQL
 * {@code reWriteBatchedInserts}, MySQL {@code rewriteBatchedStatements}).
 *
 * <p>All methods treat the query string in the simple {@code key=value} form drivers use and compare
 * parameter names case-insensitively. They do no URL decoding; they are intended for the
 * straightforward JDBC URLs the supported drivers accept.
 *
 * @since 2.10.0
 */
public final class JdbcUrls {

    private JdbcUrls() {
    }

    /**
     * Returns whether the URL already carries a query parameter with the given name (any value).
     *
     * @param url  the JDBC URL, may be null
     * @param name the parameter name (case-insensitive)
     * @return {@code true} if the parameter is present
     */
    public static boolean hasParameter(String url, String name) {
        return rawValue(url, name) != null;
    }

    /**
     * Returns whether the URL carries the given parameter set to the given value
     * (both name and value compared case-insensitively).
     *
     * @param url   the JDBC URL, may be null
     * @param name  the parameter name (case-insensitive)
     * @param value the expected value (case-insensitive)
     * @return {@code true} if present and equal
     */
    public static boolean parameterEquals(String url, String name, String value) {
        String actual = rawValue(url, name);
        return actual != null && actual.equalsIgnoreCase(value);
    }

    /**
     * Appends {@code name=value} to the URL's query string, choosing {@code ?} or {@code &} as the
     * separator. Does not check for or replace an existing parameter of the same name &mdash; use
     * {@link #hasParameter(String, String)} first if that matters.
     *
     * @param url   the JDBC URL
     * @param name  the parameter name
     * @param value the parameter value
     * @return the URL with the parameter appended
     * @throws IllegalArgumentException if any argument is null/blank
     */
    public static String appendParameter(String url, String name, String value) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        String separator = url.indexOf('?') < 0 ? "?" : "&";
        return url + separator + name + "=" + value;
    }

    /**
     * Returns the URL with the driver's batch-rewrite parameter set to {@code true}, or the URL
     * unchanged if {@code parameterName} is null/blank (the database has no such parameter) or the
     * URL already specifies the parameter (the caller's explicit value is preserved).
     *
     * <pre>{@code
     * String url = JdbcUrls.withBatchRewrite(baseUrl, support.batchRewriteUrlParameter());
     * }</pre>
     *
     * @param url           the JDBC URL
     * @param parameterName the batch-rewrite parameter name, or null if not applicable
     * @return the URL, with the parameter appended when applicable and absent
     */
    public static String withBatchRewrite(String url, String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return url;
        }
        if (hasParameter(url, parameterName)) {
            return url;
        }
        return appendParameter(url, parameterName, "true");
    }

    /** Returns the raw (un-decoded) value of the named query parameter, or null if absent. */
    private static String rawValue(String url, String name) {
        if (url == null) {
            return null;
        }
        int question = url.indexOf('?');
        if (question < 0 || question == url.length() - 1) {
            return null;
        }
        for (String pair : url.substring(question + 1).split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (key.equalsIgnoreCase(name)) {
                return equals < 0 ? "" : pair.substring(equals + 1);
            }
        }
        return null;
    }
}
