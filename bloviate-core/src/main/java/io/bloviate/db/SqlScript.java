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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A named SQL script: the text of one or more {@code ;}-terminated statements plus the
 * {@code ${name}} token values to substitute into it. The name is only a label &mdash; it appears in
 * log lines and in the message of any failure so a caller running several scripts can tell which one
 * broke.
 *
 * <p>A script is a cheap, immutable value. Where its text comes from is decided by the factory:
 * {@link #inline(String, String)}, {@link #file(Path)} or {@link #resource(String)}. File and
 * classpath text is read when the script is <em>run</em>, not when it is created, so a missing file
 * fails the run (as a {@link java.sql.SQLException}) rather than the code that assembled the
 * configuration.
 *
 * <p>Scripts are executed by {@link SqlScriptRunner}, and registered as fill hooks with
 * {@link DatabaseFiller.Builder#before(SqlScript)} and {@link DatabaseFiller.Builder#after(SqlScript)}.
 * See {@link SqlScriptRunner} for the supported SQL syntax, the token rules and the transaction
 * semantics.
 *
 * <p>Example:
 * <pre>{@code
 * SqlScript rollup = SqlScript.resource("sql/rollup.sql")
 *     .withTokens(Map.of("schema", "reporting"));
 * }</pre>
 *
 * @since 3.3.0
 * @see SqlScriptRunner
 */
public final class SqlScript {

    /** Supplies the raw script text; may fail with an {@link IOException}. */
    @FunctionalInterface
    private interface Source {
        String read() throws IOException;
    }

    private final String name;
    private final Source source;
    private final Map<String, String> tokens;

    private SqlScript(String name, Source source, Map<String, String> tokens) {
        this.name = name;
        this.source = source;
        this.tokens = tokens;
    }

    /**
     * Creates a script from SQL text held in memory.
     *
     * @param name a label for logs and error messages
     * @param sql  the script text
     * @return the script
     */
    public static SqlScript inline(String name, String sql) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        return new SqlScript(name, () -> sql, Map.of());
    }

    /**
     * Creates a script from SQL text held in memory, labelled {@code "inline"}.
     *
     * @param sql the script text
     * @return the script
     */
    public static SqlScript inline(String sql) {
        return inline("inline", sql);
    }

    /**
     * Creates a script read from a file (UTF-8) when it is run. The script's name is the file path.
     *
     * @param path the file to read
     * @return the script
     */
    public static SqlScript file(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        return new SqlScript(path.toString(), () -> Files.readString(path, StandardCharsets.UTF_8), Map.of());
    }

    /**
     * Creates a script read (UTF-8) from a classpath resource when it is run, using the thread's
     * context class loader and falling back to this library's own. The script's name is the resource
     * path.
     *
     * @param resource the classpath resource, e.g. {@code "sql/rollup.sql"}
     * @return the script
     */
    public static SqlScript resource(String resource) {
        return resource(resource, null);
    }

    /**
     * Creates a script read (UTF-8) from a classpath resource of a specific class loader when it is
     * run. The script's name is the resource path.
     *
     * @param resource    the classpath resource, e.g. {@code "sql/rollup.sql"}
     * @param classLoader the loader to resolve it with; {@code null} means the thread's context class
     *                    loader, falling back to this library's own
     * @return the script
     */
    public static SqlScript resource(String resource, ClassLoader classLoader) {
        Objects.requireNonNull(resource, "resource must not be null");
        return new SqlScript(resource, () -> readResource(resource, classLoader), Map.of());
    }

    private static String readResource(String resource, ClassLoader classLoader) throws IOException {
        try (InputStream in = openResource(resource, classLoader)) {
            if (in == null) {
                throw new FileNotFoundException("SQL script not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * An explicit loader is authoritative and gets no fallback. Otherwise the context loader is tried
     * first and, when it is absent <em>or does not have the resource</em>, this library's own loader.
     */
    private static InputStream openResource(String resource, ClassLoader classLoader) {
        if (classLoader != null) {
            return classLoader.getResourceAsStream(resource);
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream in = context != null ? context.getResourceAsStream(resource) : null;
        return in != null ? in : SqlScript.class.getResourceAsStream("/" + resource);
    }

    /**
     * Returns a copy of this script that also substitutes the given {@code ${name}} tokens. Tokens
     * accumulate across calls; a later value for the same name replaces an earlier one.
     *
     * @param values token name to replacement text; neither names nor values may be null
     * @return a new script; this one is unchanged
     */
    public SqlScript withTokens(Map<String, String> values) {
        Objects.requireNonNull(values, "values must not be null");
        Map<String, String> merged = new LinkedHashMap<>(tokens);
        merged.putAll(values);
        return new SqlScript(name, source, Map.copyOf(merged));
    }

    /**
     * The label used in logs and error messages.
     *
     * @return the script name
     */
    public String name() {
        return name;
    }

    /**
     * The {@code ${name}} substitutions applied when the script runs.
     *
     * @return an unmodifiable map of token name to replacement text
     */
    public Map<String, String> tokens() {
        return Collections.unmodifiableMap(tokens);
    }

    /**
     * Reads the raw script text, before token substitution.
     *
     * @return the script text
     * @throws IOException if a file or classpath resource cannot be read
     */
    public String read() throws IOException {
        return source.read();
    }

    @Override
    public String toString() {
        return name;
    }
}
