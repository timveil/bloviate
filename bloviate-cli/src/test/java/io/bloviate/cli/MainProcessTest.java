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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@link Main} in a child JVM, the way the jar is run. The in-process tests cover the behaviour;
 * this covers what only a real process shows: that {@code main} turns the outcome into the process exit
 * code, and what the logging binding writes (its level is read once, when the first logger is created,
 * so {@code -v} and {@code -q} cannot be observed from a JVM that has already logged).
 *
 * <p>The database is a file-based H2, since an in-memory one lives and dies with one process.
 */
class MainProcessTest {

    private static final String PASSWORD = "pr0cess-pw-Wm4";

    @TempDir
    Path directory;

    private String url;

    @BeforeEach
    void createDatabase() throws SQLException {
        url = "jdbc:h2:file:" + directory.resolve("db");
        try (Connection connection = DriverManager.getConnection(url, "sa", PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("create table customers (id int primary key, name varchar(30))");
        }
    }

    private long customers() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from customers")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private record Result(int code, String out, String err) {
    }

    private Result main(Map<String, String> environment, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Path out = directory.resolve("stdout.txt");
        Path err = directory.resolve("stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(err.toFile());
        builder.environment().remove(FillOptions.PASSWORD_ENV);
        builder.environment().putAll(environment);

        java.lang.Process process = builder.start();
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "the CLI did not finish");
        return new Result(process.exitValue(), Files.readString(out), Files.readString(err));
    }

    private Result fill(Map<String, String> environment, String... extra) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>(List.of("fill", "--url", url, "--user", "sa"));
        args.addAll(List.of(extra));
        return main(environment, args.toArray(String[]::new));
    }

    private static final Map<String, String> PASSWORD_ENV = Map.of(FillOptions.PASSWORD_ENV, PASSWORD);

    @Test
    void versionIsPrintedAndTheProcessExitsZero() throws IOException, InterruptedException {
        Result run = main(Map.of(), "--version");

        assertEquals(0, run.code(), run.err());
        assertEquals("bloviate " + System.getProperty("bloviate.expected.version") + System.lineSeparator(), run.out());
    }

    @Test
    void mainExitsWithTheCommandsExitCode() throws IOException, InterruptedException, SQLException {
        Result ok = fill(PASSWORD_ENV, "--rows", "10");
        assertEquals(0, ok.code(), ok.err());
        assertEquals(10, customers());

        Result again = fill(PASSWORD_ENV, "--rows", "10");
        assertEquals(1, again.code(), "a second fill collides on the primary key");
        assertTrue(again.err().contains("bloviate: fill failed: failed to fill table [CUSTOMERS]"), again.err());
        assertFalse(again.err().contains("\tat "), "no stack trace by default: " + again.err());

        assertEquals(2, main(Map.of(), "fill", "--url", url, "--nonsense").code());
        assertEquals(3, main(Map.of(), "fill", "--url", "jdbc:nosuchdb:x").code());
    }

    @Test
    void theDefaultLevelLogsInfoAndWarnings() throws IOException, InterruptedException {
        Result run = fill(PASSWORD_ENV, "--rows", "5", "--before", noop(), "--table-rows", "nosuch=1");

        assertEquals(0, run.code(), run.err());
        assertTrue(run.err().contains("INFO"), run.err());
        assertTrue(run.err().contains("connecting to jdbc:h2:file:"), run.err());
        assertTrue(run.err().contains("WARN"), run.err());
        assertFalse(run.err().contains("DEBUG"), run.err());
        assertEquals("", run.out(), "stdout stays free of log lines");
    }

    @Test
    void quietLogsWarningsAndErrorsOnly() throws IOException, InterruptedException {
        Result run = fill(PASSWORD_ENV, "--rows", "5", "--before", noop(), "--table-rows", "nosuch=1", "-q");

        assertEquals(0, run.code(), run.err());
        assertFalse(run.err().contains("INFO"), run.err());
        assertTrue(run.err().contains("WARN"), "warnings are not hidden: " + run.err());
        assertTrue(run.err().contains("match no table"), run.err());
    }

    @Test
    void verboseLogsDebug() throws IOException, InterruptedException {
        Result run = fill(PASSWORD_ENV, "--rows", "5", "--verbose");

        assertEquals(0, run.code(), run.err());
        assertTrue(run.err().contains("DEBUG"), run.err());
        assertTrue(run.err().contains("filling table [CUSTOMERS]"), run.err());
    }

    @Test
    void thePasswordNeverAppearsInTheOutputEvenWhenVerbose() throws IOException, InterruptedException {
        Result ok = fill(PASSWORD_ENV, "--rows", "5", "-v");
        assertEquals(0, ok.code(), ok.err());
        assertFalse((ok.out() + ok.err()).contains(PASSWORD), "logs must not echo the password");

        // a failing fill under -v prints the stack trace and every message: still no password
        Result failed = fill(PASSWORD_ENV, "--rows", "5", "-v");
        assertEquals(1, failed.code());
        assertFalse((failed.out() + failed.err()).contains(PASSWORD), failed.err());

        // a password embedded in the URL, and a wrong one, take the connection-error path
        Result embedded = main(Map.of(), "fill", "--url", url + ";USER=sa;PASSWORD=wrong-Zz1", "-v");
        assertEquals(3, embedded.code(), embedded.err());
        assertFalse((embedded.out() + embedded.err()).contains("wrong-Zz1"), embedded.err());
    }

    private String noop() throws IOException {
        return Files.writeString(directory.resolve("noop.sql"), "select 1;").toString();
    }
}
