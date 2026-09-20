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
import io.bloviate.ext.DatabaseSupport;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A fully resolved, validated {@code fill}: every default applied, every script known to exist.
 * {@link FillRunner} turns it into a connection and a {@link io.bloviate.db.DatabaseFiller}.
 *
 * @param url           the JDBC URL as the user gave it
 * @param user          the database user, or null to leave it to the URL
 * @param password      the password, or null; never printed (see {@link #toString()})
 * @param seed          the base seed
 * @param rows          the default rows per table
 * @param batchSize     the JDBC batch size
 * @param threads       the worker threads; {@code 1} fills sequentially on one connection
 * @param commit        how rows are committed
 * @param bulkLoad      how table fills are ordered
 * @param schema        the schema to fill, or null for the connection's
 * @param catalog       the catalog to fill, or null for the connection's
 * @param include       table name patterns to keep
 * @param exclude       table name patterns to drop
 * @param before        scripts to run before the fill, in order
 * @param after         scripts to run after the fill, in order
 * @param tableRows     per-table row counts
 * @param support       the database support, or null to detect it from the connection
 * @param batchRewrite  whether to add the driver's batch-rewrite parameter to the URL when it is missing
 */
public record FillPlan(
        String url,
        String user,
        String password,
        long seed,
        long rows,
        int batchSize,
        int threads,
        CommitStrategy commit,
        BulkLoadStrategy bulkLoad,
        String schema,
        String catalog,
        List<String> include,
        List<String> exclude,
        List<Path> before,
        List<Path> after,
        Map<String, Long> tableRows,
        DatabaseSupport support,
        boolean batchRewrite) {

    /** Copies the collections so a plan cannot change under a running fill. */
    public FillPlan {
        include = List.copyOf(include);
        exclude = List.copyOf(exclude);
        before = List.copyOf(before);
        after = List.copyOf(after);
        tableRows = Map.copyOf(tableRows);
    }

    /**
     * The plan without the password or the URL, which can carry one: the record's own
     * {@code toString} would print both, and a plan is the kind of object that ends up in a log line.
     *
     * @return a description that is safe to log
     */
    @Override
    public String toString() {
        return "FillPlan[seed=" + seed + ", rows=" + rows + ", batchSize=" + batchSize + ", threads=" + threads
                + ", commit=" + commit + ", bulkLoad=" + bulkLoad + ", schema=" + schema + ", catalog=" + catalog
                + ", include=" + include + ", exclude=" + exclude + ", before=" + before + ", after=" + after
                + ", tableRows=" + tableRows + ", support=" + support + ", batchRewrite=" + batchRewrite + "]";
    }
}
