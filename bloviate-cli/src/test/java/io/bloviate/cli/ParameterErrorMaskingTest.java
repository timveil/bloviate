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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * picocli echoes the offending argument in its parse-error messages and "did you mean" suggestions, and
 * parsing has failed by then, so the password is not known: the masking has to work from the shape of a
 * URL alone. None of these may print the secret, however the mistake is made.
 */
class ParameterErrorMaskingTest {

    private static final String SECRET = "s3cret";

    private static void assertMasked(int expectedCode, CliRun run) {
        assertEquals(expectedCode, run.code(), run.err());
        assertFalse(run.out().contains(SECRET), "stdout: " + run.out());
        assertFalse(run.err().contains(SECRET), "stderr: " + run.err());
    }

    @Test
    void aStrayUrlWithAPasswordInTheAuthorityIsNotEchoed() {
        // the usual mistake: the URL without --url
        CliRun run = CliRun.run("fill", "jdbc:postgresql://u:" + SECRET + "@h/db");

        assertMasked(2, run);
        assertTrue(run.err().contains("jdbc:postgresql://u:****@h/db"), "the URL is still recognizable: " + run.err());
    }

    @Test
    void aStrayUrlWithAPasswordParameterIsNotEchoed() {
        CliRun run = CliRun.run("jdbc:postgresql://h/db?user=u&password=" + SECRET);

        assertMasked(2, run);
        assertTrue(run.err().contains("password=****"), run.err());
    }

    @Test
    void aBarePasswordArgumentIsNotEchoed() {
        assertMasked(2, CliRun.run("fill", "password=" + SECRET));
        assertMasked(2, CliRun.run("fill", "--url", "jdbc:x://h/d", "--PWD=" + SECRET));
    }

    @Test
    void anAtSignInThePasswordDoesNotLeakItsTail() {
        assertMasked(2, CliRun.run("fill", "jdbc:postgresql://u:" + SECRET + "@tail@h/db"));
        assertFalse(CliRun.run("fill", "jdbc:postgresql://u:" + SECRET + "@tail@h/db").err().contains("tail@"));
    }

    @Test
    void aValuelessUrlOptionIsAUsageError() {
        assertMasked(2, CliRun.run("fill", "--url"));
    }

    @Test
    void anotherParameterErrorWhileTheUrlIsValidDoesNotEchoTheUrl() {
        CliRun run = CliRun.run("fill", "--url=jdbc:postgresql://u:" + SECRET + "@h/db", "--seed", "abc");

        assertMasked(2, run);
        assertTrue(run.err().contains("Invalid value for option '--seed'"), run.err());
    }

    @Test
    void aBadValueForTheUrlOptionItselfIsNotEchoed() {
        // an option that takes no value, given one that looks like a URL with a password
        CliRun run = CliRun.run("fill", "--no-batch-rewrite=jdbc:postgresql://u:" + SECRET + "@h/db");

        assertMasked(2, run);
    }

    @Test
    void verboseDoesNotBringItBack() {
        assertMasked(2, CliRun.run("fill", "-v", "jdbc:postgresql://u:" + SECRET + "@h/db"));
        assertMasked(2, CliRun.run("fill", "-v", "--url=jdbc:postgresql://h/db?password=" + SECRET, "--seed", "abc"));
    }

    @Test
    void aQuotedPasswordParameterIsMaskedToo() {
        assertMasked(2, CliRun.run("fill", "'jdbc:h2:mem:x;PASSWORD=" + SECRET + "'"));
        assertMasked(2, CliRun.run("fill", "\"jdbc:h2:mem:x;password=" + SECRET + "\""));
    }
}
