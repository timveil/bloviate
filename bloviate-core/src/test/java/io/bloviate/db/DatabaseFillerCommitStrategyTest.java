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

import io.bloviate.ext.BigQuerySupport;
import io.bloviate.ext.DatabaseSupport;
import io.bloviate.ext.DefaultSupport;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Docker-free coverage of the commit strategy the parallel and unordered fill paths actually use.
 * No connection is ever opened — only the strategy mapping is exercised.
 */
class DatabaseFillerCommitStrategyTest {

    /** Matches {@code DatabaseFiller.DEFAULT_PARALLEL_COMMIT_BATCHES}. */
    private static final int DEFAULT_PARALLEL_COMMIT_BATCHES = 64;

    private static CommitStrategy effectiveStrategy(DatabaseSupport support, CommitStrategy configured) {
        DatabaseConfiguration configuration = new DatabaseConfiguration.Builder(128, 10, support)
                .commitStrategy(configured)
                .build();

        return new DatabaseFiller.Builder(new UnusableDataSource(), configuration)
                .threads(4)
                .build()
                .effectiveParallelCommitStrategy();
    }

    @Test
    void upgradesConnectionDefaultToABoundedCommitCadence() {
        // a pooled worker must not be left on the connection's autocommit, and a single per-table
        // commit would hold a whole partition open in one server-side transaction
        assertEquals(CommitStrategy.everyNBatches(DEFAULT_PARALLEL_COMMIT_BATCHES),
                effectiveStrategy(new DefaultSupport(), CommitStrategy.connectionDefault()));
    }

    @Test
    void leavesConnectionDefaultAloneWhenTheSupportPrefersIt() {
        // BigQuery: each executeBatch is already one atomic query job, while setAutoCommit(false)
        // opens a session and disables the driver's load-job path
        assertEquals(CommitStrategy.connectionDefault(),
                effectiveStrategy(new BigQuerySupport(), CommitStrategy.connectionDefault()));
    }

    @Test
    void honorsAnExplicitStrategyEvenWhenTheSupportPrefersOtherwise() {
        // the hook only suppresses the engine's own default; a caller's explicit choice still wins
        assertEquals(CommitStrategy.perTable(),
                effectiveStrategy(new BigQuerySupport(), CommitStrategy.perTable()));
        assertEquals(CommitStrategy.everyNBatches(8),
                effectiveStrategy(new BigQuerySupport(), CommitStrategy.everyNBatches(8)));
    }

    @Test
    void honorsAnExplicitStrategyForOrdinarySupports() {
        assertEquals(CommitStrategy.perTable(),
                effectiveStrategy(new DefaultSupport(), CommitStrategy.perTable()));
    }

    /** A {@link DataSource} that fails loudly if anything actually tries to connect. */
    private static final class UnusableDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("this test must not open a connection");
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
