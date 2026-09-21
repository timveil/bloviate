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

import com.zaxxer.hikari.HikariDataSource;
import io.bloviate.ext.CockroachDBSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Issue #633, the CockroachDB counterpart of {@link PostgresConstraintFillTest} and
 * {@link PostgresDateCheckFillTest}: filling a schema with an enum type and {@code CHECK}
 * constraints produces only values that satisfy them.
 *
 * <p>The enum column is the strongest signal. CockroachDB's driver reports it as {@code VARCHAR},
 * so before constraints were read there the fill generated an arbitrary string and the insert was
 * rejected; a successful fill proves the enum's labels were read and used. The rest of the schema
 * covers the spellings CockroachDB stores that PostgreSQL does not: {@code BETWEEN} verbatim and
 * {@code extract()} with a comma.
 *
 * <p>The container is built here rather than through {@link BaseCockroachTest} because it needs
 * {@code stringtype=unspecified}: every allowed value binds as a string, which the driver would
 * otherwise send typed as {@code varchar} and an enum or integer column would reject.
 */
class CockroachConstraintFillTest extends BaseDatabaseTestCase {

    private static final int ROWS = 500;

    @Test
    void generatedValuesSatisfyChecksAndEnums() throws SQLException {
        DatabaseConfiguration configuration =
                new DatabaseConfiguration(256, ROWS, new CockroachDBSupport(), null, 42L);

        try (CockroachContainer database = new CockroachContainer(TestImages.COCKROACH)
                .withUrlParam("stringtype", "unspecified")
                .withInitScript("create_constraints.cockroachdb.sql")
                .withCommand("start-single-node --insecure --store=type=mem,size=.75")) {

            database.start();

            try (HikariDataSource dataSource = (HikariDataSource) getDataSource(database);
                 Connection connection = dataSource.getConnection()) {

                // the fill only completes if the enum and CHECK constraints are honored
                new DatabaseFiller.Builder(connection, configuration).build().fill();

                assertRowCount(connection, "constrained", ROWS);
                assertRowCount(connection, "month_dates", ROWS);
                // length(code) is a function of the column: unsupported, so it is skipped with a warning
                // and the column keeps its type default — the fill must still complete
                assertRowCount(connection, "unsupported_check", ROWS);

                // every column conforms to its constraint
                assertCount(connection, "select count(*) from constrained where status not in ('NEW','PAID','SHIPPED','CANCELLED')", 0);
                assertCount(connection, "select count(*) from constrained where rating < 1 or rating > 5", 0);
                assertCount(connection, "select count(*) from constrained where priority not in (1,2,3)", 0);
                assertCount(connection, "select count(*) from constrained where grade not in ('A','B','C','D','F')", 0);
                assertCount(connection, "select count(*) from constrained where amount < 0 or amount > 9999.99", 0);
                assertCount(connection, "select count(*) from constrained where score < 0 or score > 100", 0);

                // first-of-period dates, in both spellings and on every column type
                assertCount(connection, "select count(*) from month_dates where date_trunc('month', d_trunc) <> d_trunc", 0);
                assertCount(connection, "select count(*) from month_dates where extract(day from d_extract) <> 1", 0);
                assertCount(connection, "select count(*) from month_dates where date_trunc('month', ts_trunc) <> ts_trunc", 0);
                assertCount(connection, "select count(*) from month_dates where date_trunc('month', tstz_trunc) <> tstz_trunc", 0);
                assertCount(connection, "select count(*) from month_dates where date_trunc('quarter', d_quarter) <> d_quarter", 0);
                assertCount(connection, "select count(*) from month_dates where date_trunc('year', tstz_year) <> tstz_year", 0);

                // and the generators vary across the allowed space rather than pinning one value
                assertAtLeast(connection, "select count(distinct status) from constrained", 2);
                assertAtLeast(connection, "select count(distinct rating) from constrained", 2);
                assertAtLeast(connection, "select count(distinct grade) from constrained", 2);
                assertAtLeast(connection, "select count(distinct d_trunc) from month_dates", 2);
            }
        }
    }
}
