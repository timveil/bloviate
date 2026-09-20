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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the command line that are not a fill: {@code --help}, {@code --version}, the bare command,
 * and how the password gets in (and that it never comes back out).
 */
class CommandLineTest {

    private static final String PASSWORD = "s3cr3t-pw-Xk9";

    @TempDir
    Path directory;

    @Test
    void helpListsEveryFlagTheExitCodesAndTheDriverNote() {
        CliRun run = CliRun.run("fill", "--help");

        assertEquals(0, run.code());
        for (String flag : new String[]{"--url", "--user", "--password-file", "--seed", "--rows", "--table-rows",
                "--batch-size", "--threads", "--commit", "--commit-batches", "--bulk-load", "--schema", "--catalog",
                "--include", "--exclude", "--before", "--after", "--support", "--no-batch-rewrite", "--verbose",
                "--quiet", "--help", "--version"}) {
            assertTrue(run.out().contains(flag), flag + " missing from:\n" + run.out());
        }
        assertTrue(run.out().contains("Exit codes:"), run.out());
        for (String code : new String[]{ExitCodes.HELP_OK, ExitCodes.HELP_FILL_FAILED, ExitCodes.HELP_USAGE,
                ExitCodes.HELP_CONNECTION}) {
            // picocli wraps long descriptions, so compare on the first words
            String description = code.split(":", 2)[1];
            description = description.substring(0, Math.min(15, description.length()));
            assertTrue(run.out().contains(description), code + " missing from:\n" + run.out());
        }
        assertTrue(run.out().contains(FillOptions.PASSWORD_ENV), run.out());
        assertTrue(run.out().contains("java -cp bloviate-cli.jar:postgresql.jar io.bloviate.cli.Main"), run.out());
    }

    @Test
    void thereIsNoPasswordOnTheCommandLine() {
        CliRun help = CliRun.run("fill", "--help");
        assertFalse(help.out().contains("--password "), "no --password option: " + help.out());
        assertFalse(help.out().contains("--password="), help.out());
        assertTrue(help.out().contains("--password-file"), help.out());

        CliRun attempt = CliRun.run("fill", "--url", "jdbc:h2:mem:x", "--password", PASSWORD);

        assertEquals(2, attempt.code());
        assertTrue(attempt.err().contains("Unknown option"), attempt.err());
    }

    @Test
    void topLevelHelpListsTheFillSubcommandAndTheExitCodes() {
        CliRun run = CliRun.run("--help");

        assertEquals(0, run.code());
        assertTrue(run.out().contains("fill"), run.out());
        assertTrue(run.out().contains("Exit codes:"), run.out());
    }

    @Test
    void versionIsTheRealProjectVersion() {
        String expected = System.getProperty("bloviate.expected.version");
        assertTrue(expected != null && !expected.isBlank(), "surefire must pass -Dbloviate.expected.version");

        assertEquals("bloviate " + expected + System.lineSeparator(), CliRun.run("--version").out());
        assertEquals("bloviate " + expected + System.lineSeparator(), CliRun.run("fill", "--version").out());
        assertEquals(expected, VersionProvider.version());
    }

    @Test
    void theBareCommandPrintsUsageAndIsAUsageError() {
        CliRun run = CliRun.run();

        assertEquals(2, run.code());
        assertTrue(run.err().contains("Usage: bloviate"), run.err());
    }

    @Test
    void anUnknownSubcommandIsAUsageError() {
        CliRun run = CliRun.run("frobnicate");

        assertEquals(2, run.code());
        assertTrue(run.err().startsWith("bloviate: Unmatched argument"), run.err());
    }

    @Test
    void thePasswordComesFromTheEnvironment() throws SQLException {
        try (H2Database database = H2Database.named("cli_pw_env", "sa", PASSWORD, H2Database.ORDERS_SCHEMA)) {
            CliRun run = CliRun.fill(Map.of(FillOptions.PASSWORD_ENV, PASSWORD), database.url(),
                    "--user", "sa", "--rows", "5", "--include", "customers");

            assertEquals(0, run.code(), run.err());
            assertEquals(5, database.count("customers"));
        }
    }

    @Test
    void thePasswordComesFromAFileAndItsTrailingNewlineIsIgnored() throws SQLException, IOException {
        Path file = Files.writeString(directory.resolve("pw"), PASSWORD + "\n");
        try (H2Database database = H2Database.named("cli_pw_file", "sa", PASSWORD, H2Database.ORDERS_SCHEMA)) {
            CliRun run = CliRun.fill(database.url(), "--user", "sa", "--password-file", file.toString(),
                    "--rows", "5", "--include", "customers");

            assertEquals(0, run.code(), run.err());
            assertEquals(5, database.count("customers"));
        }
    }

    @Test
    void thePasswordFileWinsOverTheEnvironment() throws SQLException, IOException {
        Path file = Files.writeString(directory.resolve("pw"), PASSWORD);
        try (H2Database database = H2Database.named("cli_pw_both", "sa", PASSWORD, H2Database.ORDERS_SCHEMA)) {
            CliRun run = CliRun.fill(Map.of(FillOptions.PASSWORD_ENV, "not-the-password"), database.url(),
                    "--user", "sa", "--password-file", file.toString(), "--rows", "5", "--include", "customers");

            assertEquals(0, run.code(), run.err());
        }
    }

    @Test
    void aPasswordInTheUrlStillWorks() throws SQLException {
        try (H2Database database = H2Database.named("cli_pw_url", "sa", PASSWORD, H2Database.ORDERS_SCHEMA)) {
            CliRun run = CliRun.fill(database.url() + ";USER=sa;PASSWORD=" + PASSWORD, "--rows", "5", "--include", "customers");

            assertEquals(0, run.code(), run.err());
            assertEquals(5, database.count("customers"));
        }
    }

    @Test
    void aWrongPasswordIsAConnectionErrorThatDoesNotEchoIt() throws SQLException {
        try (H2Database database = H2Database.named("cli_pw_wrong", "sa", PASSWORD, H2Database.ORDERS_SCHEMA)) {
            String wrong = "wrong-pw-Qz7";

            CliRun fromEnvironment = CliRun.fill(Map.of(FillOptions.PASSWORD_ENV, wrong), database.url(), "--user", "sa", "-v");
            assertEquals(3, fromEnvironment.code(), fromEnvironment.err());
            assertFalse((fromEnvironment.out() + fromEnvironment.err()).contains(wrong), fromEnvironment.err());

            CliRun fromUrl = CliRun.fill(database.url() + ";USER=sa;PASSWORD=" + wrong, "-v");
            assertEquals(3, fromUrl.code(), fromUrl.err());
            assertFalse((fromUrl.out() + fromUrl.err()).contains(wrong), fromUrl.err());
            assertTrue(fromUrl.err().contains("PASSWORD=****"), "the URL is shown with the password masked: " + fromUrl.err());
        }
    }

    @Test
    void aMissingOrEmptyPasswordFileIsAUsageError() throws IOException {
        CliRun missing = CliRun.fill("jdbc:h2:mem:x", "--password-file", directory.resolve("nope").toString());
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains("--password-file cannot be read"), missing.err());

        Path empty = Files.writeString(directory.resolve("empty"), "\n");
        CliRun blank = CliRun.fill("jdbc:h2:mem:x", "--password-file", empty.toString());
        assertEquals(2, blank.code());
        assertTrue(blank.err().contains("--password-file is empty"), blank.err());
    }
}
