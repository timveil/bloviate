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

import io.bloviate.cli.FillOptions.BulkLoadMode;
import io.bloviate.cli.FillOptions.CommitMode;
import io.bloviate.cli.FillOptions.SupportName;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code bloviate fill}: fills the tables of a JDBC database with generated data.
 *
 * <p>This class only parses flags. It states them as a {@link FillOptions} layer ({@link #flags()}),
 * which is resolved into a {@link FillPlan} and run by {@link FillRunner}. Nothing is defaulted here, so
 * a setting the user did not type stays unstated and a lower layer (a configuration file) can supply it.
 *
 * <p>Every outcome is turned into an exit code here rather than thrown, so the command behaves the same
 * whether it runs under {@link Main#main(String[])} or in-process from a test.
 */
@Command(
        name = "fill",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        sortOptions = false,
        description = "Fills the tables of a database with generated, reproducible, foreign-key-consistent data.",
        optionListHeading = "%nOptions:%n",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                ExitCodes.HELP_OK,
                ExitCodes.HELP_FILL_FAILED,
                ExitCodes.HELP_USAGE,
                ExitCodes.HELP_CONNECTION
        },
        footerHeading = "%nNotes:%n",
        footer = {
                "  The password is read from " + FillOptions.PASSWORD_ENV + " or --password-file, never from",
                "  an argument (it would show in the process list); one in the JDBC URL works",
                "  too. No JDBC driver is bundled: put yours on the classpath and run the main",
                "  class ('java -jar' ignores -cp):",
                "    java -cp bloviate-cli.jar:postgresql.jar io.bloviate.cli.Main fill ..."
        })
public final class FillCommand implements Callable<Integer> {

    /** A message longer than this is shortened in the middle (the whole stack trace is there with -v). */
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MESSAGE_HEAD = 160;
    private static final int MESSAGE_TAIL = 300;
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int MESSAGE_LEAD = 60;

    /** Set by picocli. */
    @Spec
    private CommandSpec spec;

    @Option(names = "--url", paramLabel = "<jdbc-url>",
            description = "JDBC URL of the database to fill (required).")
    private String url;

    @Option(names = "--user", paramLabel = "<user>",
            description = "Database user, if not part of the URL.")
    private String user;

    @Option(names = "--password-file", paramLabel = "<file>",
            description = "File holding the password (read instead of " + FillOptions.PASSWORD_ENV + ").")
    private Path passwordFile;

    @Option(names = "--seed", paramLabel = "<n>",
            description = "Base seed; the same seed on the same schema yields the same data (default: 0).")
    private Long seed;

    @Option(names = "--rows", paramLabel = "<n>",
            description = "Rows to generate per table (default: 100).")
    private Long rows;

    @Option(names = "--table-rows", paramLabel = "<table=N>", split = ",",
            description = "Rows for one table, overriding --rows; repeatable or comma-separated. "
                    + "A name that matches no table is an error.")
    private Map<String, Long> tableRows;

    @Option(names = "--batch-size", paramLabel = "<n>",
            description = "Rows per JDBC batch insert (default: 1000).")
    private Integer batchSize;

    @Option(names = "--threads", paramLabel = "<n>",
            description = "Worker threads; above 1 fills independent tables concurrently, one connection each (default: 1).")
    private Integer threads;

    @Option(names = "--commit", paramLabel = "<mode>", converter = CommitModeConverter.class,
            description = "When rows are committed: connection-default, per-table or every-n-batches "
                    + "(default: connection-default).")
    private CommitMode commit;

    @Option(names = "--commit-batches", paramLabel = "<n>",
            description = "Batches between commits; required by, and only for, --commit every-n-batches.")
    private Integer commitBatches;

    @Option(names = "--bulk-load", paramLabel = "<mode>", converter = BulkLoadModeConverter.class,
            description = "ordered (parents before children, the default) or unordered (constraints off, "
                    + "every table at once; needs --threads above 1 and PostgreSQL or MySQL).")
    private BulkLoadMode bulkLoad;

    @Option(names = "--schema", paramLabel = "<schema>",
            description = "Schema to fill instead of the connection's current one.")
    private String schema;

    @Option(names = "--catalog", paramLabel = "<catalog>",
            description = "Catalog to fill instead of the connection's current one (the database, on MySQL and MariaDB).")
    private String catalog;

    @Option(names = "--include", paramLabel = "<pattern>", split = ",",
            description = "Fill only tables matching this pattern (* any run of characters, ? one character, "
                    + "case-insensitive); repeatable or comma-separated. A pattern that matches nothing is an error.")
    private List<String> include;

    @Option(names = "--exclude", paramLabel = "<pattern>", split = ",",
            description = "Leave tables matching this pattern unfilled, e.g. one an --after script computes; "
                    + "same syntax as --include. A table another selected table references cannot be excluded.")
    private List<String> exclude;

    @Option(names = "--before", paramLabel = "<file>",
            description = "SQL script to run before the fill, e.g. to truncate the tables; repeatable, run in order.")
    private List<Path> before;

    @Option(names = "--after", paramLabel = "<file>",
            description = "SQL script to run after the fill, e.g. to compute a rollup; repeatable, run in order. "
                    + "A failing script stops the run and the ones after it do not run.")
    private List<Path> after;

    @Option(names = "--support", paramLabel = "<database>", converter = SupportNameConverter.class,
            description = "Database support to use instead of detecting it from the connection: "
                    + "postgres, cockroachdb, mysql, mariadb, h2, sqlite, bigquery or default.")
    private SupportName support;

    @Option(names = "--no-batch-rewrite",
            description = "Do not add the driver's batch-rewrite parameter (reWriteBatchedInserts, "
                    + "rewriteBatchedStatements) to PostgreSQL, MySQL and MariaDB URLs that lack it.")
    private boolean noBatchRewrite;

    @ArgGroup(exclusive = true)
    private Verbosity verbosity;

    /** {@code -v} and {@code -q}: a group so picocli rejects using both. */
    private static final class Verbosity {

        @Option(names = {"-v", "--verbose"},
                description = "Log at DEBUG and print stack traces on failure.")
        private boolean verbose;

        @Option(names = {"-q", "--quiet"},
                description = "Log warnings and errors only.")
        private boolean quiet;
    }

    /** Where {@value FillOptions#PASSWORD_ENV} is read from; {@link System#getenv()} unless a test says otherwise. */
    private final Map<String, String> environment;

    /** Creates the command for the process environment. */
    public FillCommand() {
        this(System.getenv());
    }

    /**
     * Creates the command for the given environment.
     *
     * @param environment the environment variables to read
     */
    FillCommand(Map<String, String> environment) {
        this.environment = environment;
    }

    /**
     * What the flags say, and nothing else: every setting not typed is left unstated. A configuration
     * file layer goes underneath this ({@code fileLayer.overlay(flags())}).
     *
     * @return the flags as a layer
     */
    FillOptions flags() {
        return new FillOptions(url, user, passwordFile, seed, rows, batchSize, threads, commit, commitBatches,
                bulkLoad, schema, catalog, include, exclude, before, after, tableRows, support,
                noBatchRewrite ? Boolean.FALSE : null);
    }

    /**
     * Runs the fill.
     *
     * @return the exit code (see {@link ExitCodes})
     */
    @Override
    public Integer call() {
        boolean verbose = verbosity != null && verbosity.verbose;
        boolean quiet = verbosity != null && verbosity.quiet;
        // before anything logs: the logging binding reads its level once, when the first logger is created
        configureLogging(verbose, quiet);

        PrintWriter err = spec.commandLine().getErr();
        Secrets secrets = Secrets.none();
        try {
            FillPlan plan = flags().resolve(environment);
            secrets = new Secrets(plan.url(), plan.password());
            new FillRunner(plan).run();
            return ExitCodes.OK;
        } catch (IllegalArgumentException e) {
            return report(err, secrets, verbose, ExitCodes.USAGE, e.getMessage(), e);
        } catch (ConnectionFailedException e) {
            return report(err, secrets, verbose, ExitCodes.CONNECTION, e.getMessage() + ": " + describeCause(e), e);
        } catch (SQLException | RuntimeException e) {
            // a RuntimeException here is the engine giving up on the schema (a column type it cannot
            // generate, say), which is a failed fill like any other
            return report(err, secrets, verbose, ExitCodes.FILL_FAILED, "fill failed: " + describe(e), e);
        }
    }

    /** Sets the level of the SLF4J simple binding from {@code -v}/{@code -q}; neither leaves it alone. */
    static void configureLogging(boolean verbose, boolean quiet) {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        } else if (quiet) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        }
    }

    /** Prints the one-line failure (and, under {@code -v}, the stack trace) with every secret masked. */
    private static int report(PrintWriter err, Secrets secrets, boolean verbose, int exitCode, String message,
                              Throwable failure) {
        err.println(BloviateCommand.NAME + ": " + secrets.scrub(singleLine(message)));
        if (verbose) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            err.print(secrets.scrub(trace.toString()));
        }
        err.flush();
        return exitCode;
    }

    /**
     * The exception's message and, when it does not already say so, the deepest cause's, which is
     * usually the driver's own. A JDBC batch failure hides its real error one step further, in
     * {@link SQLException#getNextException()}, so that chain is followed too.
     */
    static String describe(Throwable failure) {
        String message = singleLine(messageOf(failure));
        String deepest = singleLine(messageOf(deepest(failure)));
        String shown = abbreviate(message);
        // compare only the start: a driver's message often repeats in its wrapper with the row data differing
        String lead = deepest.substring(0, Math.min(MESSAGE_LEAD, deepest.length()));
        // judged on what is printed: abbreviating cuts the middle out, which is where a failed batch
        // insert's message has the reason, after the multi-row statement it quotes
        return shown.contains(lead) ? shown : shown + " (caused by: " + abbreviate(deepest) + ")";
    }

    private static String describeCause(Throwable failure) {
        return failure.getCause() == null ? singleLine(messageOf(failure)) : describe(failure.getCause());
    }

    private static Throwable deepest(Throwable failure) {
        Throwable current = failure;
        // bounded: a cause chain can be cyclic
        for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
            Throwable next = current.getCause();
            if (next == null && current instanceof SQLException sql) {
                next = sql.getNextException();
            }
            if (next == null) {
                break;
            }
            current = next;
        }
        return current;
    }

    /**
     * Shortens a very long message in the middle. A failed batch insert's message quotes the whole
     * multi-row statement, and the reason it failed is at the end.
     */
    private static String abbreviate(String message) {
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MESSAGE_HEAD) + " ... " + message.substring(message.length() - MESSAGE_TAIL);
    }

    private static String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message.strip();
    }

    /** Driver messages span lines (a PostgreSQL error carries its detail on the next); the report is one. */
    private static String singleLine(String message) {
        return message.replaceAll("\\s*\\R\\s*", " ").strip();
    }

    private static <E extends Enum<E>> E convert(Class<E> type, String text) {
        try {
            return FillOptions.parse(type, text);
        } catch (IllegalArgumentException e) {
            TypeConversionException conversion = new TypeConversionException(e.getMessage());
            conversion.initCause(e);
            throw conversion;
        }
    }

    /** Reads {@code --commit} values in their kebab-case spelling. */
    static final class CommitModeConverter implements ITypeConverter<CommitMode> {
        @Override
        public CommitMode convert(String value) {
            return FillCommand.convert(CommitMode.class, value);
        }
    }

    /** Reads {@code --bulk-load} values. */
    static final class BulkLoadModeConverter implements ITypeConverter<BulkLoadMode> {
        @Override
        public BulkLoadMode convert(String value) {
            return FillCommand.convert(BulkLoadMode.class, value);
        }
    }

    /** Reads {@code --support} values. */
    static final class SupportNameConverter implements ITypeConverter<SupportName> {
        @Override
        public SupportName convert(String value) {
            return FillCommand.convert(SupportName.class, value);
        }
    }
}
