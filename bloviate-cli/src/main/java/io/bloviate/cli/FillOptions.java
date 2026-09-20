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

package io.bloviate.cli;

import io.bloviate.db.BulkLoadStrategy;
import io.bloviate.db.CommitStrategy;
import io.bloviate.ext.BigQuerySupport;
import io.bloviate.ext.CockroachDBSupport;
import io.bloviate.ext.DatabaseSupport;
import io.bloviate.ext.DefaultSupport;
import io.bloviate.ext.H2Support;
import io.bloviate.ext.MariaDBSupport;
import io.bloviate.ext.MySQLSupport;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.ext.SQLiteSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * One layer of {@code fill} settings, as the user stated them: every component is {@code null} (or an
 * empty list/map) when that setting was not stated. A layer does not know defaults and is not validated;
 * {@link #resolve(Map)} turns the final, merged layer into a {@link FillPlan}.
 *
 * <p>This is the seam a declarative configuration file layers under the flags: parse the file into a
 * {@code FillOptions}, then {@code fromConfigFile.overlay(fromFlags).resolve(env)}. The flags win over
 * the file, and the file wins over the built-in defaults, without the command knowing about either.
 *
 * @param url          the JDBC URL
 * @param user         the database user
 * @param passwordFile a file holding the password (its content, minus the trailing line break)
 * @param seed         the base seed
 * @param rows         the default number of rows per table
 * @param batchSize    the JDBC batch size
 * @param threads      the number of worker threads
 * @param commit       the commit mode
 * @param commitBatches the batch cadence of {@link CommitMode#EVERY_N_BATCHES}
 * @param bulkLoad     the bulk-load mode
 * @param schema       the schema to fill
 * @param catalog      the catalog to fill
 * @param include      table name patterns to keep
 * @param exclude      table name patterns to drop
 * @param before       SQL script files to run before the fill
 * @param after        SQL script files to run after the fill
 * @param tableRows    per-table row counts, by table name
 * @param support      the database support to use instead of the auto-detected one
 * @param batchRewrite whether to add the driver's batch-rewrite parameter to the URL
 */
public record FillOptions(
        String url,
        String user,
        Path passwordFile,
        Long seed,
        Long rows,
        Integer batchSize,
        Integer threads,
        CommitMode commit,
        Integer commitBatches,
        BulkLoadMode bulkLoad,
        String schema,
        String catalog,
        List<String> include,
        List<String> exclude,
        List<Path> before,
        List<Path> after,
        Map<String, Long> tableRows,
        SupportName support,
        Boolean batchRewrite) {

    /** The environment variable the password is read from when there is no password file. */
    public static final String PASSWORD_ENV = "BLOVIATE_PASSWORD";

    /** Default rows per table. */
    static final long DEFAULT_ROWS = 100L;

    /** Default JDBC batch size. */
    static final int DEFAULT_BATCH_SIZE = 1000;

    /** Default base seed. */
    static final long DEFAULT_SEED = 0L;

    /** Default thread count: sequential, on a single connection. */
    static final int DEFAULT_THREADS = 1;

    /** How the engine commits inserted rows; the {@code --commit} values. */
    public enum CommitMode {
        /** Leave the connection's autocommit alone. */
        CONNECTION_DEFAULT,
        /** One commit per table. */
        PER_TABLE,
        /** A commit every {@code --commit-batches} batches. */
        EVERY_N_BATCHES
    }

    /** How the engine orders table fills; the {@code --bulk-load} values. */
    public enum BulkLoadMode {
        /** Parents before children (the default). */
        ORDERED,
        /** Constraints off, every table at once (needs threads above 1 and a supporting database). */
        UNORDERED
    }

    /** The database supports {@code --support} can name. */
    public enum SupportName {
        /** PostgreSQL. */
        POSTGRES(PostgresSupport::new),
        /** CockroachDB. */
        COCKROACHDB(CockroachDBSupport::new),
        /** MySQL. */
        MYSQL(MySQLSupport::new),
        /** MariaDB. */
        MARIADB(MariaDBSupport::new),
        /** H2. */
        H2(H2Support::new),
        /** SQLite. */
        SQLITE(SQLiteSupport::new),
        /** Google BigQuery. */
        BIGQUERY(BigQuerySupport::new),
        /** The generic fallback. */
        DEFAULT(DefaultSupport::new);

        private final Supplier<DatabaseSupport> factory;

        SupportName(Supplier<DatabaseSupport> factory) {
            this.factory = factory;
        }

        /**
         * Creates the support.
         *
         * @return a new instance
         */
        DatabaseSupport create() {
            return factory.get();
        }
    }

    /** Normalizes unstated lists and maps to empty ones and copies them, so a layer is immutable. */
    public FillOptions {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
        before = before == null ? List.of() : List.copyOf(before);
        after = after == null ? List.of() : List.copyOf(after);
        tableRows = tableRows == null ? Map.of() : Map.copyOf(tableRows);
    }

    /**
     * A layer that states nothing.
     *
     * @return an empty layer
     */
    static FillOptions empty() {
        return new FillOptions(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /**
     * Layers {@code higher} over this one: a setting {@code higher} states replaces this layer's, one it
     * leaves out keeps this layer's. A list or map is replaced as a whole when {@code higher} has any
     * entry (a flag's {@code --include} list is not appended to the file's).
     *
     * @param higher the layer that wins
     * @return the merged layer
     */
    public FillOptions overlay(FillOptions higher) {
        Objects.requireNonNull(higher, "higher must not be null");
        return new FillOptions(
                pick(higher.url, url),
                pick(higher.user, user),
                pick(higher.passwordFile, passwordFile),
                pick(higher.seed, seed),
                pick(higher.rows, rows),
                pick(higher.batchSize, batchSize),
                pick(higher.threads, threads),
                pick(higher.commit, commit),
                pick(higher.commitBatches, commitBatches),
                pick(higher.bulkLoad, bulkLoad),
                pick(higher.schema, schema),
                pick(higher.catalog, catalog),
                higher.include.isEmpty() ? include : higher.include,
                higher.exclude.isEmpty() ? exclude : higher.exclude,
                higher.before.isEmpty() ? before : higher.before,
                higher.after.isEmpty() ? after : higher.after,
                higher.tableRows.isEmpty() ? tableRows : higher.tableRows,
                pick(higher.support, support),
                pick(higher.batchRewrite, batchRewrite));
    }

    private static <T> T pick(T higher, T lower) {
        return higher != null ? higher : lower;
    }

    /**
     * Applies the defaults and validates the result. Nothing here touches the database, so every
     * problem it reports is a usage or configuration error.
     *
     * @param environment the environment variables (for {@value #PASSWORD_ENV})
     * @return the plan to run
     * @throws IllegalArgumentException if a setting is missing, out of range or contradictory, or a
     *                                  script or password file cannot be read
     */
    public FillPlan resolve(Map<String, String> environment) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("missing required option '--url' (the JDBC URL of the database to fill)");
        }
        long resolvedRows = rows == null ? DEFAULT_ROWS : rows;
        if (resolvedRows < 0) {
            throw new IllegalArgumentException("--rows must be >= 0: " + resolvedRows);
        }
        int resolvedBatchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        if (resolvedBatchSize < 1) {
            throw new IllegalArgumentException("--batch-size must be >= 1: " + resolvedBatchSize);
        }
        int resolvedThreads = threads == null ? DEFAULT_THREADS : threads;
        if (resolvedThreads < 1) {
            throw new IllegalArgumentException("--threads must be >= 1: " + resolvedThreads);
        }
        BulkLoadMode resolvedBulk = bulkLoad == null ? BulkLoadMode.ORDERED : bulkLoad;
        if (resolvedBulk == BulkLoadMode.UNORDERED && resolvedThreads == 1) {
            throw new IllegalArgumentException("--bulk-load unordered needs --threads greater than 1: "
                    + "it fills every table at once, which a single connection cannot do");
        }

        requireNotBlank("--schema", schema);
        requireNotBlank("--catalog", catalog);
        include.forEach(pattern -> requireNotBlank("--include pattern", pattern));
        exclude.forEach(pattern -> requireNotBlank("--exclude pattern", pattern));

        return new FillPlan(
                url,
                user,
                readPassword(environment),
                seed == null ? DEFAULT_SEED : seed,
                resolvedRows,
                resolvedBatchSize,
                resolvedThreads,
                commitStrategy(),
                resolvedBulk == BulkLoadMode.UNORDERED ? BulkLoadStrategy.unorderedBulk() : BulkLoadStrategy.ordered(),
                schema,
                catalog,
                include,
                exclude,
                requireReadable("--before", before),
                requireReadable("--after", after),
                checkedTableRows(),
                support == null ? null : support.create(),
                batchRewrite == null || batchRewrite);
    }

    private static void requireNotBlank(String what, String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }

    private CommitStrategy commitStrategy() {
        CommitMode mode = commit == null ? CommitMode.CONNECTION_DEFAULT : commit;
        if (mode == CommitMode.EVERY_N_BATCHES) {
            if (commitBatches == null) {
                throw new IllegalArgumentException("--commit every-n-batches needs --commit-batches N");
            }
            if (commitBatches < 1) {
                throw new IllegalArgumentException("--commit-batches must be >= 1: " + commitBatches);
            }
            return CommitStrategy.everyNBatches(commitBatches);
        }
        if (commitBatches != null) {
            throw new IllegalArgumentException("--commit-batches only applies to --commit every-n-batches");
        }
        return mode == CommitMode.PER_TABLE ? CommitStrategy.perTable() : CommitStrategy.connectionDefault();
    }

    private Map<String, Long> checkedTableRows() {
        // a table name is case-insensitive to the core, so two spellings of one table are a conflict
        Map<String, Long> seen = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Long> entry : tableRows.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw new IllegalArgumentException("--table-rows needs a table name: expected table=N");
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("--table-rows " + entry.getKey() + " must be >= 0: " + entry.getValue());
            }
            if (seen.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("--table-rows names table '" + entry.getKey() + "' more than once");
            }
        }
        return tableRows;
    }

    private static List<Path> requireReadable(String flag, List<Path> scripts) {
        List<Path> checked = new ArrayList<>();
        for (Path script : scripts) {
            if (!Files.isRegularFile(script) || !Files.isReadable(script)) {
                throw new IllegalArgumentException(flag + " script is not a readable file: " + script);
            }
            checked.add(script);
        }
        return checked;
    }

    /**
     * The password, or null when none is configured: the password file if one is given, otherwise
     * {@value #PASSWORD_ENV}. A password in the JDBC URL needs no help from here.
     */
    private String readPassword(Map<String, String> environment) {
        if (passwordFile != null) {
            String text;
            try {
                text = Files.readString(passwordFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("--password-file cannot be read: " + passwordFile, e);
            }
            // only the line terminator a text editor or `echo` adds: the password itself may end in a space
            String password = text.replaceFirst("(\\r?\\n)+$", "");
            if (password.isEmpty()) {
                throw new IllegalArgumentException("--password-file is empty: " + passwordFile);
            }
            return password;
        }
        String fromEnvironment = environment.get(PASSWORD_ENV);
        return fromEnvironment == null || fromEnvironment.isEmpty() ? null : fromEnvironment;
    }

    /**
     * Parses a kebab-case enum value ({@code every-n-batches}) case-insensitively.
     *
     * @param type the enum type
     * @param text what the user typed
     * @param <E>  the enum type
     * @return the constant
     * @throws IllegalArgumentException naming the accepted values
     */
    static <E extends Enum<E>> E parse(Class<E> type, String text) {
        for (E constant : type.getEnumConstants()) {
            if (label(constant).equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("expected one of " + labels(type) + " but was '" + text + "'");
    }

    /**
     * The values of an enum as the user types them.
     *
     * @param type the enum type
     * @param <E>  the enum type
     * @return the kebab-case names, in declaration order
     */
    static <E extends Enum<E>> Set<String> labels(Class<E> type) {
        Set<String> labels = new LinkedHashSet<>();
        for (E constant : type.getEnumConstants()) {
            labels.add(label(constant));
        }
        return labels;
    }

    private static String label(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
