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

package io.bloviate.bench;

import io.bloviate.db.Column;
import io.bloviate.ext.PostgresSupport;
import io.bloviate.file.ColumnDefinition;
import io.bloviate.file.CsvFile;
import io.bloviate.file.FileDefinition;
import io.bloviate.file.FlatFileGenerator;
import io.bloviate.file.PipeDelimitedFile;
import io.bloviate.file.TabDelimitedFile;
import io.bloviate.gen.StaticStringGenerator;
import io.bloviate.util.RandomGenerators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end throughput of the flat-file subsystem ({@link FlatFileGenerator#generate()}) — the only
 * benchmark that exercises {@link io.bloviate.file} at all. Each measured op writes a full file of
 * {@link #ROWS} rows to a real (temp) path, so the timing covers everything a CLI/file user actually
 * pays: per-cell {@code generateAsString()}, Commons-CSV formatting and quoting, and buffered IO.
 *
 * <p>Two axes are crossed:
 * <ul>
 *   <li>{@link Format} — the three delimiters the subsystem supports (CSV, tab, pipe), so a change to
 *       {@code CSVFormat} selection or delimiter handling shows up per format.</li>
 *   <li>{@link Content} — what the cells contain. {@code MIXED} is a realistic wide row (real
 *       type-driven generators resolved exactly as {@link io.bloviate.db.TableFiller} resolves them),
 *       giving the headline "rows/second to disk" number. {@code PLAIN} and {@code ESCAPED} are
 *       constant-string rows that differ <em>only</em> in whether the value embeds the delimiter and a
 *       quote: with generation cost held flat between them, {@code PLAIN} vs {@code ESCAPED} isolates
 *       the cost of Commons-CSV's quoting/escaping path.</li>
 * </ul>
 *
 * <p>Tunable via {@code -Dfile.rows} (default 5,000 rows per file).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class FileGenBenchmark {

    /** Fixed seed so the realistic ({@code MIXED}) generators emit the same value stream every run. */
    private static final long SEED = 42L;

    /** Rows written per measured op; small enough to stay quick, large enough to amortize file open. */
    private static final long ROWS = Long.getLong("file.rows", 5_000L);

    /** The three delimited formats {@link FlatFileGenerator} supports. */
    public enum Format {
        CSV,
        TDV,
        PIPE
    }

    /**
     * What every cell holds. {@code MIXED} drives realistic, type-varied generators (the common case);
     * {@code PLAIN} and {@code ESCAPED} are constant strings of identical generation cost that differ
     * only in needing CSV quoting, isolating the escape path.
     */
    public enum Content {
        MIXED,
        PLAIN,
        ESCAPED
    }

    /** A constant value with no delimiter or quote — never triggers Commons-CSV quoting. */
    private static final String PLAIN_VALUE = "abcdefghijklmnop";

    /** A constant value embedding every delimiter and a quote — forces quoting/escaping in all formats. */
    private static final String ESCAPED_VALUE = "a,b\tc|d \"quoted\" e";

    @Param
    public Format format;

    @Param
    public Content content;

    private FlatFileGenerator generator;
    private Path tempDir;
    private String baseName;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("bloviate-filebench");
        // FlatFileGenerator appends the format extension to this base name itself
        baseName = tempDir.resolve("out").toString();

        generator = new FlatFileGenerator.Builder(baseName)
                .output(fileDefinition(format))
                .addAll(columns(content))
                .rows(ROWS)
                .build();
    }

    @Benchmark
    public void generate() {
        generator.generate();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        // remove the written file(s) and the temp directory
        if (tempDir != null) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    private static FileDefinition fileDefinition(Format format) {
        return switch (format) {
            case CSV -> new CsvFile();
            case TDV -> new TabDelimitedFile();
            case PIPE -> new PipeDelimitedFile();
        };
    }

    /**
     * Builds the column definitions for a content mode. {@code MIXED} reuses {@link BenchColumns#wideRow()}
     * with real generators (so the file write pays realistic generation cost); the constant-string modes
     * keep the same column count but emit a fixed value, holding generation cost flat so the format/escape
     * cost is what varies.
     */
    private static List<ColumnDefinition> columns(Content content) {
        if (content == Content.MIXED) {
            PostgresSupport support = new PostgresSupport();
            long seed = SEED;
            List<ColumnDefinition> definitions = new ArrayList<>();
            for (Column column : BenchColumns.wideRow()) {
                definitions.add(new ColumnDefinition(column.name(),
                        support.getDataGenerator(column, RandomGenerators.create(seed++))));
            }
            return definitions;
        }

        String value = content == Content.ESCAPED ? ESCAPED_VALUE : PLAIN_VALUE;
        int width = BenchColumns.wideRow().size();
        List<ColumnDefinition> definitions = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            StaticStringGenerator generator = new StaticStringGenerator.Builder(RandomGenerators.create(SEED))
                    .value(value)
                    .build();
            definitions.add(new ColumnDefinition("c" + i, generator));
        }
        return definitions;
    }
}
