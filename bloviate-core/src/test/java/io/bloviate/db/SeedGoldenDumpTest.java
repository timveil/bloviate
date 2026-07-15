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

import io.bloviate.ext.H2Support;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Seed-reproducibility gate: fills a pinned schema with a pinned seed and compares the complete
 * data dump byte-for-byte against a golden file checked into test resources.
 *
 * <p>The guarantee this enforces (see the "Design Invariants" section of CONTRIBUTING.md): for a
 * given Bloviate version, the same schema and seed must produce identical data on every run, on
 * every platform and JDK. A release may deliberately change what a seed produces — improvements
 * happen — but never accidentally. Any change that alters a generator's draw sequence, the
 * per-column seed derivation, or metadata traversal order that feeds seeding will fail this test
 * loudly instead of shipping silently. The schema deliberately covers foreign-key chains, a column
 * participating in two foreign keys (whose resolution depends on foreign-key grouping order), and
 * a spread of type families (integers, strings, numerics, temporals, UUID, binary, boolean).
 *
 * <p><strong>If this test fails, first decide whether the data change is intentional.</strong> If
 * it is not, fix the regression. If it is (a deliberate, release-noted improvement), regenerate
 * the golden file by running the test with {@code -Dbloviate.regenerate.golden=true}, review the
 * diff, and note the behavior change in the commit so it reaches the changelog.
 *
 * <p>Everything that feeds the dump is pinned: the H2 database name (it is the JDBC catalog, which
 * participates in per-column seed derivation), the seed, the row count, the batch size, and the
 * session time zone (java.sql.Timestamp binds convert to the wall-clock TIMESTAMP column through
 * it, so it must not float with the machine's zone).
 */
class SeedGoldenDumpTest {

    private static final String JDBC_URL = "jdbc:h2:mem:seed_golden";
    private static final long SEED = 42L;
    private static final long ROWS = 200L;
    private static final int BATCH_SIZE = 100;
    private static final String[] TABLES = {"REGION", "NATION", "CUSTOMER", "ORDERS", "BRIDGE"};

    @Test
    void dumpMatchesGoldenFile() throws Exception {
        String actual = fillAndDump();

        // always drop the actual dump next to the reports so a failure can be diffed directly
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/seed-golden-actual.txt"), actual, StandardCharsets.UTF_8);

        if (Boolean.getBoolean("bloviate.regenerate.golden")) {
            Path golden = Path.of("src/test/resources/golden/seed-golden-expected.txt");
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            fail("golden file regenerated at " + golden + " — review the diff and re-run without the property");
        }

        assertEquals(readGolden(), actual,
                "Generated data for the pinned schema and seed no longer matches the golden dump. "
                        + "If this change is unintentional, it is a reproducibility regression — fix it. "
                        + "If it is a deliberate improvement, regenerate the golden file and release-note "
                        + "the behavior change. See the class javadoc.");
    }

    private static String fillAndDump() throws SQLException, IOException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
            String ddl;
            try (InputStream in = SeedGoldenDumpTest.class.getResourceAsStream("/golden/seed-golden-schema.sql")) {
                ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            try (Statement statement = connection.createStatement()) {
                for (String sql : ddl.split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
                // the bridge table's column has two parents; disable enforcement so its rows insert
                // regardless of which parent the resolution order follows
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                // pin the SESSION time zone: java.sql.Timestamp values are converted to the
                // wall-clock TIMESTAMP column through it, so the dump must not depend on the
                // machine's zone. (TimeZone.setDefault is not enough — H2 caches the JVM zone
                // statically the first time any test loads it.)
                statement.execute("SET TIME ZONE 'UTC'");
            }

            new DatabaseFiller.Builder(connection,
                    new DatabaseConfiguration(BATCH_SIZE, ROWS, new H2Support(), null, SEED))
                    .build()
                    .fill();

            StringBuilder dump = new StringBuilder();
            for (String table : TABLES) {
                dump.append("== ").append(table).append(" ==\n");
                try (Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("select * from " + table + " order by 1")) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    while (rs.next()) {
                        for (int i = 1; i <= metaData.getColumnCount(); i++) {
                            if (metaData.getColumnType(i) == Types.VARBINARY || metaData.getColumnType(i) == Types.BINARY) {
                                byte[] bytes = rs.getBytes(i);
                                dump.append(bytes == null ? "null" : HexFormat.of().formatHex(bytes));
                            } else {
                                dump.append(rs.getString(i));
                            }
                            dump.append('|');
                        }
                        dump.append('\n');
                    }
                }
            }
            return dump.toString();
        }
    }

    private static String readGolden() throws IOException {
        try (InputStream in = SeedGoldenDumpTest.class.getResourceAsStream("/golden/seed-golden-expected.txt")) {
            if (in == null) {
                fail("golden file missing: run once with -Dbloviate.regenerate.golden=true to create it");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
