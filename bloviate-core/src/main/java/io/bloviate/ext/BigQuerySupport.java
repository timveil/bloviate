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

package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.db.Database;
import io.bloviate.gen.BigDecimalGenerator;
import io.bloviate.gen.ByteGenerator;
import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.IntervalGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.SimpleStringGenerator;
import io.bloviate.gen.SqlExpressionGenerator;
import io.bloviate.gen.WktPointGenerator;

import java.sql.Connection;
import java.sql.JDBCType;
import java.util.Locale;
import java.util.Map;

/**
 * Google BigQuery-specific {@link DatabaseSupport}, written against the
 * <a href="https://github.com/Two-Bear-Capital/tbc-bq-jdbc">tbc-bq-jdbc</a> driver
 * (<code>vc.tbc:tbc-bq-jdbc</code>, <strong>4.4.0 or later</strong> — see the value-expression note below).
 *
 * <p>BigQuery is an analytical engine, and it diverges from the OLTP databases Bloviate
 * otherwise targets in three ways this class has to account for:
 *
 * <ul>
 *   <li><strong>{@code COLUMN_SIZE} is a type maximum, not a declared width.</strong> A bare
 *       {@code STRING} column reports 2,097,152 and a bare {@code BYTES} column 10,485,760 &mdash;
 *       the type limits, not anything the schema asked for. Sizes are clamped <em>downward</em> to
 *       {@link #MAX_STRING_LENGTH}/{@link #MAX_BYTES_LENGTH}, which leaves a declared
 *       {@code STRING(20)} honest while taming the bare form. This matters because a batch buffers
 *       every value as a query parameter before flushing, and the whole chunk has to fit inside one
 *       {@code jobs.insert} request.</li>
 *   <li><strong>The write side is narrower than the read side.</strong> The driver reads
 *       {@code JSON}, {@code GEOGRAPHY} and {@code INTERVAL} back as {@link JDBCType#VARCHAR} and
 *       {@code DATETIME} as {@link JDBCType#TIMESTAMP}, but has no parameter binding that produces
 *       those types, and BigQuery will not implicitly coerce a {@code STRING}/{@code TIMESTAMP}
 *       parameter into them. Their values are therefore generated as text and constructed by the
 *       server through a {@link io.bloviate.gen.DataGenerator#valueExpression() value expression}
 *       &mdash; see {@link #VALUE_EXPRESSIONS}. This requires tbc-bq-jdbc <strong>4.4.0 or
 *       later</strong>: earlier versions only collapse a batch whose {@code VALUES} tuple is
 *       placeholders-only, so a wrapped column silently degrades to one query job per row.</li>
 *   <li><strong>Keys are always {@code NOT ENFORCED}.</strong> BigQuery accepts declarative
 *       {@code PRIMARY KEY}/{@code FOREIGN KEY} constraints but never enforces them, and the driver
 *       surfaces them through {@code getPrimaryKeys}/{@code getImportedKeys}. Bloviate's FK-aware
 *       ordering therefore works, and {@link #supportsBulkLoad()} is safe to enable.</li>
 * </ul>
 *
 * <p><strong>Supported types:</strong> every scalar type &mdash; {@code STRING}, {@code BYTES},
 * {@code INT64}, {@code FLOAT64}, {@code NUMERIC}, {@code BIGNUMERIC} (within {@code NUMERIC}
 * range), {@code BOOL}, {@code DATE}, {@code TIME}, {@code TIMESTAMP}, {@code DATETIME},
 * {@code JSON}, {@code GEOGRAPHY} and {@code INTERVAL}.
 *
 * <p><strong>Unsupported types:</strong> the composite ones &mdash; {@code ARRAY}, {@code STRUCT}
 * and {@code RANGE}. {@code ARRAY} and {@code STRUCT} need a shape Bloviate has no representation
 * for; {@code RANGE} would need two parameters for one column, which the fill engine's
 * one-parameter-per-column binding cannot express. Supply a per-column generator through
 * {@code ColumnConfiguration} or {@link GeneratorRegistry.Builder#registerColumnNamePattern} to
 * fill them; the driver implements {@link Connection#createArrayOf} and
 * {@link Connection#createStruct}, so a hand-written generator can write composites today.
 *
 * <p><strong>Required driver settings:</strong> {@code includeStructFields=false} (the default) and
 * {@code metadataLazyLoad=false} (the default). With struct fields spliced in, {@code getColumns}
 * emits dotted sub-field rows alongside their parent and the generated {@code INSERT} is
 * structurally invalid; with lazy metadata, an unfiltered {@code getTables}/{@code getColumns}
 * returns nothing and the fill silently does no work.
 *
 * @since 3.1.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class BigQuerySupport extends AbstractDatabaseSupport {

    /**
     * Cap on generated {@code STRING} length. A bare {@code STRING} column reports BigQuery's type
     * maximum of 2,097,152; a declared {@code STRING(n)} below this cap is honored exactly.
     *
     * <p>{@link SimpleStringGenerator} independently caps itself at 2000 characters, so this is an
     * additional ~8x reduction on top of that, not the only thing standing between a fill and
     * two-megabyte cells.
     */
    public static final int MAX_STRING_LENGTH = 256;

    /**
     * Cap on generated {@code BYTES} length. A bare {@code BYTES} column reports BigQuery's type
     * maximum of 10,485,760; a declared {@code BYTES(n)} below this cap is honored exactly.
     *
     * <p>{@link ByteGenerator} independently caps itself at 25 bytes, which is below this value, so
     * today this clamp only bites for a declared {@code BYTES(n)} where {@code n} is between 25 and
     * 128 &mdash; where it changes nothing either. It is kept as a stated bound so the intent
     * survives a change to the generator's own cap.
     */
    public static final int MAX_BYTES_LENGTH = 128;

    /** Maximum precision of BigQuery's {@code NUMERIC} type. */
    public static final int MAX_NUMERIC_PRECISION = 38;

    /**
     * Maximum scale of BigQuery's {@code NUMERIC} type. The driver binds every {@code BigDecimal}
     * as {@code NUMERIC}, so this caps {@code BIGNUMERIC} columns too: a {@code BIGNUMERIC} reports
     * scale 38, and a value carrying more than 9 fractional digits is rejected as an out-of-range
     * {@code NUMERIC} parameter regardless of what the destination column could hold.
     */
    public static final int MAX_NUMERIC_SCALE = 9;

    /**
     * SQL that constructs a value for each type the driver cannot bind, keyed by BigQuery type name.
     *
     * <p>These are the types whose {@code getColumns} shape is indistinguishable from a bindable one
     * &mdash; {@code JSON}, {@code GEOGRAPHY} and {@code INTERVAL} all read back as
     * {@link JDBCType#VARCHAR}, {@code DATETIME} as {@link JDBCType#TIMESTAMP} &mdash; but which
     * have no parameter type of their own, and which BigQuery will not implicitly coerce into. The
     * value is generated as text (or, for {@code DATETIME}, as a timestamp) and turned into the
     * column's type by the server.
     *
     * <p>Wrapping costs the batch collapse: tbc-bq-jdbc keeps a batch whose {@code VALUES} tuple is
     * not placeholders-only off its NDJSON load-job path, since that path writes bound values
     * directly and would drop the wrapping. The DML collapse itself still applies, but only from
     * driver 4.4.0 &mdash; earlier versions require a placeholders-only tuple and fall back to one
     * query job per row, which is correct but very slow.
     */
    private static final Map<String, String> VALUE_EXPRESSIONS = Map.of(
            "JSON", "PARSE_JSON(?)",
            "GEOGRAPHY", "ST_GEOGFROMTEXT(?)",
            "INTERVAL", "CAST(? AS INTERVAL)",
            "DATETIME", "CAST(? AS DATETIME)");

    /** Creates the BigQuery support with its default configuration. */
    public BigQuerySupport() {
    }

    /**
     * Wraps a generator so its text is turned into {@code typeName} by the server.
     *
     * @param delegate the generator producing the value's text
     * @param typeName the BigQuery type name, which must have a {@link #VALUE_EXPRESSIONS} entry
     * @return the wrapped generator
     */
    private static DataGenerator<?> constructed(DataGenerator<?> delegate, String typeName) {
        return SqlExpressionGenerator.of(delegate, VALUE_EXPRESSIONS.get(typeName));
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // STRING, plus the three types the driver reads back as VARCHAR but cannot bind: their
        // values are generated as text and constructed server-side (see VALUE_EXPRESSIONS).
        registry.put(JDBCType.VARCHAR, (column, random) -> switch (baseTypeName(column)) {
            case "STRING" -> new SimpleStringGenerator.Builder(random)
                    .size(clamp(column.maxSize(), MAX_STRING_LENGTH))
                    .build();
            case "JSON" -> constructed(new JsonbGenerator.Builder(random).build(), "JSON");
            case "GEOGRAPHY" -> constructed(new WktPointGenerator.Builder(random).build(), "GEOGRAPHY");
            case "INTERVAL" -> constructed(new IntervalGenerator.Builder(random).build(), "INTERVAL");
            default -> throw unsupported(column, "no generator is registered for this type");
        });

        registry.put(JDBCType.VARBINARY, (column, random) -> {
            if (!"BYTES".equals(baseTypeName(column))) {
                throw unsupported(column, "no generator is registered for this type");
            }
            return new ByteGenerator.Builder(random)
                    .size(clamp(column.maxSize(), MAX_BYTES_LENGTH))
                    .build();
        });

        // NUMERIC and BIGNUMERIC both arrive as JDBC NUMERIC. Clamping BIGNUMERIC's reported
        // (76, 38) down to NUMERIC's (38, 9) is always safe: a NUMERIC-range value is assignable
        // to a BIGNUMERIC column, and the driver has no BIGNUMERIC parameter binding anyway.
        GeneratorFactory bigDecimal = (column, random) -> {
            int precision = clamp(column.maxSize(), MAX_NUMERIC_PRECISION);
            Integer reportedScale = column.maxDigits();
            int scale = reportedScale == null || reportedScale < 0
                    ? 0
                    : Math.min(reportedScale, MAX_NUMERIC_SCALE);
            return new BigDecimalGenerator.Builder(random)
                    .precision(precision)
                    .digits(Math.min(scale, precision))
                    .build();
        };
        registry.put(JDBCType.NUMERIC, bigDecimal);
        registry.put(JDBCType.DECIMAL, bigDecimal);

        // TIMESTAMP and DATETIME both arrive as JDBC TIMESTAMP. Only TIMESTAMP can be bound, so a
        // DATETIME column takes the same generated instant and casts it server-side. The cast is
        // interpreted in UTC, which is deterministic and therefore reproducible.
        GeneratorFactory inheritedTimestamp = registry.get(JDBCType.TIMESTAMP);
        registry.put(JDBCType.TIMESTAMP, (column, random) -> {
            DataGenerator<?> timestamp = inheritedTimestamp.create(column, random);
            return "DATETIME".equals(baseTypeName(column)) ? constructed(timestamp, "DATETIME") : timestamp;
        });

        // RANGE<...> is the only type the driver maps to OTHER.
        registry.put(JDBCType.OTHER, (column, random) -> {
            throw unsupported(column, "RANGE values have no JDBC parameter binding");
        });

        registry.put(JDBCType.ARRAY, (column, random) -> {
            throw unsupported(column, "array element generation is not yet supported");
        });

        // Replace rather than remove the inherited SqlStructGenerator: it cannot know a BigQuery
        // struct's shape and would silently generate garbage, and removing it would surface the
        // generic "JDBCType [STRUCT] not supported" instead of the actionable message below.
        registry.put(JDBCType.STRUCT, (column, random) -> {
            throw unsupported(column, "a struct's field shape is not derivable from JDBC metadata");
        });
    }

    /**
     * Enabled because BigQuery's {@code PRIMARY KEY}/{@code FOREIGN KEY} constraints are always
     * {@code NOT ENFORCED}: there is no enforcement to suspend, so an unordered bulk fill needs no
     * preparation and carries no correctness risk. Referential consistency does not depend on
     * insert order either &mdash; a foreign-key column is seeded from its parent primary-key
     * column's seed and replays the same value sequence whenever it is filled.
     *
     * @return always {@code true}
     */
    @Override
    public boolean supportsBulkLoad() {
        return true;
    }

    /**
     * A no-op: BigQuery never enforces key constraints, so there is nothing to disable. This is
     * deliberate, not a stub &mdash; it exists so {@link #supportsBulkLoad()} can return
     * {@code true} and unlock the unordered fill path. The connection is not touched.
     *
     * @param connection ignored
     * @param database   ignored
     * @return a handle recording that nothing was disabled
     */
    @Override
    public BulkLoadHandle disableConstraints(Connection connection, Database database) {
        return BulkLoadHandle.of("no-op: BigQuery PRIMARY KEY/FOREIGN KEY are NOT ENFORCED");
    }

    /**
     * A no-op, mirroring {@link #disableConstraints}. The connection is not touched.
     *
     * @param connection ignored
     * @param database   ignored
     * @param handle     ignored
     */
    @Override
    public void enableConstraints(Connection connection, Database database, BulkLoadHandle handle) {
        // nothing was disabled; see disableConstraints
    }

    /**
     * Enabled because an engine-managed transaction is pure cost on BigQuery: each
     * {@code executeBatch} is already a single atomic query job, while {@code setAutoCommit(false)}
     * lazily starts a BigQuery <em>session</em> (per-connection overhead and quota) and disables the
     * driver's NDJSON load-job path, which is the fastest way to bulk-insert.
     *
     * @return always {@code true}
     */
    @Override
    public boolean prefersConnectionDefaultCommit() {
        return true;
    }

    /**
     * Returns {@code null}: rewriting a batch into a multi-row {@code INSERT} is unconditional in
     * tbc-bq-jdbc, so there is no URL parameter to recommend and the fill engine should stay quiet
     * rather than warn about a missing one.
     *
     * <p>This overrides nothing behaviorally &mdash; the interface default is already {@code null}
     * &mdash; but it is stated explicitly so the reasoning is recorded where someone would look for
     * it. In particular, load-job tuning ({@code batchLoadThreshold}) does <em>not</em> belong here:
     * this hook's contract is a batch-rewrite toggle, and that property takes an integer, so
     * advertising it would make the engine emit wrong advice.
     *
     * @return always {@code null}
     */
    @Override
    public String batchRewriteUrlParameter() {
        return null;
    }

    /**
     * Reduces a BigQuery {@code TYPE_NAME} to its base type. The driver reports the raw
     * {@code INFORMATION_SCHEMA.COLUMNS.data_type} text, so names arrive parameterized
     * ({@code STRING(20)}, {@code NUMERIC(10, 2)}) or with type arguments ({@code ARRAY<INT64>},
     * {@code STRUCT<a INT64, b STRING>}, {@code RANGE<DATE>}).
     *
     * @param column the column whose type name to normalize
     * @return the upper-cased base type name, or an empty string when unreported
     */
    private static String baseTypeName(Column column) {
        String typeName = column.typeName();
        if (typeName == null || typeName.isBlank()) {
            return "";
        }
        String upper = typeName.trim().toUpperCase(Locale.ROOT);
        int cut = upper.length();
        int paren = upper.indexOf('(');
        if (paren >= 0) {
            cut = paren;
        }
        int angle = upper.indexOf('<');
        if (angle >= 0 && angle < cut) {
            cut = angle;
        }
        return upper.substring(0, cut).trim();
    }

    /**
     * Clamps a reported size downward to a cap. Clamping only ever shortens, so a generated value
     * always remains valid for the declared column; an unreported or non-positive size falls back
     * to the cap.
     *
     * @param reported the size reported by {@code getColumns}, may be null
     * @param cap      the maximum value to allow
     * @return the clamped size
     */
    private static int clamp(Integer reported, int cap) {
        if (reported == null || reported <= 0) {
            return cap;
        }
        return Math.min(reported, cap);
    }

    private static UnsupportedOperationException unsupported(Column column, String reason) {
        return new UnsupportedOperationException(String.format(
                "BigQuery type [%s] on column [%s.%s] cannot be filled by a plain parameter binding: %s. "
                        + "Supply a per-column generator via ColumnConfiguration (or "
                        + "GeneratorRegistry.registerColumnNamePattern) — see docs/DATABASE_SUPPORT.md#bigquery.",
                column.typeName(), column.tableName(), column.name(), reason));
    }
}
