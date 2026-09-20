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
import io.bloviate.db.BulkLoadStrategy;
import io.bloviate.db.CommitStrategy;
import io.bloviate.ext.H2Support;
import io.bloviate.ext.PostgresSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings model on its own: defaults, layering (the seam a configuration file plugs into), and the
 * batch-rewrite URL rule, none of which needs a database.
 */
class FillOptionsTest {

    private static final Map<String, String> NO_ENV = Map.of();

    @TempDir
    Path directory;

    private static FillOptions url(String url) {
        return new FillOptions(url, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void resolvingAppliesTheDefaults() {
        FillPlan plan = url("jdbc:h2:mem:x").resolve(NO_ENV);

        assertEquals("jdbc:h2:mem:x", plan.url());
        assertNull(plan.user());
        assertNull(plan.password());
        assertEquals(0L, plan.seed());
        assertEquals(100L, plan.rows());
        assertEquals(1000, plan.batchSize());
        assertEquals(1, plan.threads());
        assertEquals(CommitStrategy.connectionDefault(), plan.commit());
        assertEquals(BulkLoadStrategy.ordered(), plan.bulkLoad());
        assertNull(plan.support(), "auto-detect unless told otherwise");
        assertTrue(plan.batchRewrite());
        assertTrue(plan.include().isEmpty() && plan.exclude().isEmpty() && plan.before().isEmpty() && plan.after().isEmpty());
    }

    @Test
    void aHigherLayerWinsAndALowerLayerFillsTheGaps() {
        FillOptions file = new FillOptions("jdbc:h2:mem:file", "file-user", null, 7L, 500L, 250, 4,
                CommitMode.PER_TABLE, null, null, "file_schema", null, List.of("a*"), List.of("b*"), List.of(),
                List.of(), Map.of("t", 1L), SupportName.H2, false);
        FillOptions flags = new FillOptions("jdbc:h2:mem:flag", null, null, null, 9L, null, null,
                null, null, null, null, null, List.of("c*"), null, null, null, null, null, null);

        FillPlan plan = file.overlay(flags).resolve(NO_ENV);

        assertEquals("jdbc:h2:mem:flag", plan.url(), "the flag wins");
        assertEquals("file-user", plan.user(), "the file fills what the flags leave out");
        assertEquals(7L, plan.seed());
        assertEquals(9L, plan.rows(), "the flag wins");
        assertEquals(250, plan.batchSize());
        assertEquals(4, plan.threads());
        assertEquals(CommitStrategy.perTable(), plan.commit());
        assertEquals("file_schema", plan.schema());
        assertEquals(List.of("c*"), plan.include(), "a list is replaced, not appended to");
        assertEquals(List.of("b*"), plan.exclude());
        assertEquals(Map.of("t", 1L), plan.tableRows());
        assertInstanceOf(H2Support.class, plan.support());
        assertFalse(plan.batchRewrite());
    }

    @Test
    void anEmptyLayerChangesNothing() {
        FillOptions base = url("jdbc:h2:mem:x");

        assertEquals(base, base.overlay(FillOptions.empty()));
        assertEquals(base, FillOptions.empty().overlay(base));
    }

    @Test
    void theCommitStrategyMapsToTheCore() {
        assertEquals(CommitStrategy.everyNBatches(5),
                withCommit(CommitMode.EVERY_N_BATCHES, 5).resolve(NO_ENV).commit());
        assertEquals(CommitStrategy.perTable(), withCommit(CommitMode.PER_TABLE, null).resolve(NO_ENV).commit());
        assertThrows(IllegalArgumentException.class, () -> withCommit(CommitMode.PER_TABLE, 5).resolve(NO_ENV));
        assertThrows(IllegalArgumentException.class, () -> withCommit(null, 5).resolve(NO_ENV));
    }

    private static FillOptions withCommit(CommitMode mode, Integer batches) {
        return new FillOptions("jdbc:h2:mem:x", null, null, null, null, null, null, mode, batches, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void unorderedBulkLoadMapsToTheCoreAndNeedsThreads() {
        FillOptions bulk = new FillOptions("jdbc:h2:mem:x", null, null, null, null, null, 4, null, null,
                BulkLoadMode.UNORDERED, null, null, null, null, null, null, null, null, null);

        assertEquals(BulkLoadStrategy.unorderedBulk(), bulk.resolve(NO_ENV).bulkLoad());
        assertThrows(IllegalArgumentException.class, () -> bulk.overlay(
                new FillOptions(null, null, null, null, null, null, 1, null, null, null, null, null,
                        null, null, null, null, null, null, null)).resolve(NO_ENV));
    }

    @Test
    void thePasswordFileBeatsTheEnvironmentAndKeepsInnerWhitespace() throws IOException {
        Path file = Files.writeString(directory.resolve("pw"), " pass word \r\n");
        FillOptions options = new FillOptions("jdbc:h2:mem:x", null, file, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        assertEquals(" pass word ", options.resolve(Map.of(FillOptions.PASSWORD_ENV, "env")).password());
        assertEquals("env", url("jdbc:h2:mem:x").resolve(Map.of(FillOptions.PASSWORD_ENV, "env")).password());
        assertNull(url("jdbc:h2:mem:x").resolve(Map.of(FillOptions.PASSWORD_ENV, "")).password());
    }

    @Test
    void thePlanNeverPrintsThePasswordOrTheUrl() {
        FillPlan plan = url("jdbc:postgresql://u:hunter2@host/db?password=hunter2")
                .resolve(Map.of(FillOptions.PASSWORD_ENV, "hunter2"));

        assertFalse(plan.toString().contains("hunter2"), plan.toString());
        assertFalse(plan.toString().contains("host"), plan.toString());
    }

    @Test
    void kebabCaseValuesParseCaseInsensitively() {
        assertEquals(CommitMode.EVERY_N_BATCHES, FillOptions.parse(CommitMode.class, "Every-N-Batches"));
        assertEquals(BulkLoadMode.UNORDERED, FillOptions.parse(BulkLoadMode.class, "unordered"));
        assertEquals(SupportName.COCKROACHDB, FillOptions.parse(SupportName.class, "COCKROACHDB"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FillOptions.parse(CommitMode.class, "every_n_batches"));
        assertEquals("expected one of [connection-default, per-table, every-n-batches] but was 'every_n_batches'", e.getMessage());
    }

    // ---- batch-rewrite URL parameter ----

    private static String rewritten(String url, boolean batchRewrite, SupportName support) {
        FillOptions options = new FillOptions(url, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, support, batchRewrite ? null : Boolean.FALSE);
        return FillRunner.effectiveUrl(options.resolve(NO_ENV));
    }

    @Test
    void theBatchRewriteParameterIsAppendedForPostgresMysqlAndMariaDbUrls() {
        assertEquals("jdbc:postgresql://h/db?reWriteBatchedInserts=true", rewritten("jdbc:postgresql://h/db", true, null));
        assertEquals("jdbc:postgresql://h/db?ssl=true&reWriteBatchedInserts=true",
                rewritten("jdbc:postgresql://h/db?ssl=true", true, null));
        assertEquals("jdbc:mysql://h/db?rewriteBatchedStatements=true", rewritten("jdbc:mysql://h/db", true, null));
        assertEquals("jdbc:mariadb://h/db?rewriteBatchedStatements=true", rewritten("jdbc:mariadb://h/db", true, null));
    }

    @Test
    void anExistingBatchRewriteParameterIsNeverTouched() {
        String off = "jdbc:postgresql://h/db?reWriteBatchedInserts=false";
        assertEquals(off, rewritten(off, true, null));
        String lower = "jdbc:mysql://h/db?REWRITEBATCHEDSTATEMENTS=false&x=1";
        assertEquals(lower, rewritten(lower, true, null));
    }

    @Test
    void theBatchRewriteParameterIsLeftOffWhenDisabledOrNotApplicable() {
        assertEquals("jdbc:postgresql://h/db", rewritten("jdbc:postgresql://h/db", false, null));
        assertEquals("jdbc:h2:mem:x", rewritten("jdbc:h2:mem:x", true, null));
        assertEquals("jdbc:sqlite:/tmp/x.db", rewritten("jdbc:sqlite:/tmp/x.db", true, null));
        // CockroachDB has no such parameter, so choosing it explicitly means none is added
        assertEquals("jdbc:postgresql://h/db", rewritten("jdbc:postgresql://h/db", true, SupportName.COCKROACHDB));
        assertEquals("jdbc:postgresql://h/db?reWriteBatchedInserts=true", rewritten("jdbc:postgresql://h/db", true, SupportName.POSTGRES));
        assertInstanceOf(PostgresSupport.class, SupportName.POSTGRES.create());
    }
}
