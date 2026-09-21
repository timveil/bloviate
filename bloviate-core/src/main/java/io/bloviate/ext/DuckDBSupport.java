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
import io.bloviate.gen.BitStringGenerator;
import io.bloviate.gen.ByteGenerator;
import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.JsonbGenerator;
import io.bloviate.gen.LongGenerator;
import io.bloviate.gen.ShortGenerator;
import io.bloviate.gen.SqlTimestampGenerator;
import io.bloviate.gen.UUIDGenerator;
import io.bloviate.gen.WeightedCategoricalGenerator;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * DuckDB-specific {@link DatabaseSupport}.
 *
 * <p>DuckDB is an embedded, in-process analytical database that nonetheless enforces primary and
 * foreign keys, so the engine's dependency-ordered fill works against it unchanged. It runs from a
 * {@code jdbc:duckdb:} URL with no server and no container.
 *
 * <p>Most columns map through the cross-database defaults in {@link AbstractDatabaseSupport}. What
 * this class customizes is everything the driver's metadata does not describe accurately enough to
 * generate from:
 *
 * <ul>
 *   <li><strong>Unsigned integers.</strong> {@code UTINYINT}, {@code USMALLINT} and
 *       {@code UINTEGER} are reported as the next <em>signed</em> JDBC type up
 *       ({@link JDBCType#SMALLINT}, {@link JDBCType#INTEGER}, {@link JDBCType#BIGINT}) with only
 *       {@code TYPE_NAME} saying they are unsigned. The widened default would generate negative
 *       values, which DuckDB rejects with a conversion error, so these are ranged from zero.
 *       {@code UBIGINT} and {@code UHUGEINT} arrive as {@link JDBCType#OTHER} and are handled
 *       below.</li>
 *   <li><strong>{@code BIT}.</strong> DuckDB's {@code BIT} is a bit <em>string</em>, not a single
 *       bit, and the driver reports no {@code COLUMN_SIZE}; the default would bind a boolean.</li>
 *   <li><strong>{@link JDBCType#OTHER}.</strong> DuckDB puts a lot behind it —
 *       {@code UUID}, {@code JSON}, {@code HUGEINT}, {@code UBIGINT}, {@code UHUGEINT},
 *       {@code BIGNUM}, the second/millisecond/nanosecond timestamps, {@code ENUM}, {@code MAP},
 *       {@code INTERVAL} and list and array types — so it is dispatched on {@code TYPE_NAME}.</li>
 *   <li><strong>{@code ENUM}.</strong> {@code TYPE_NAME} carries the declared labels
 *       ({@code ENUM('ok', 'sad')}), so one of them is generated rather than an arbitrary string.
 *       This is the same outcome the {@code CHECK}/enum constraint reader gives elsewhere, reached
 *       from the type itself.</li>
 * </ul>
 *
 * <p>The composite and variable-width types — {@code STRUCT}, {@code MAP}, {@code UNION},
 * {@code INTERVAL}, {@code BIGNUM} and list or array types such as {@code INTEGER[]} — are not
 * supported: each needs a bound value the JDBC driver has no portable parameter form for. They
 * throw {@link UnsupportedOperationException} from {@link #getDataGenerator} rather than generating
 * something the insert would reject. Give those columns an explicit generator, or leave them out of
 * the fill.
 *
 * @since 3.10.0
 * @see AbstractDatabaseSupport
 * @see DatabaseSupport
 */
public class DuckDBSupport extends AbstractDatabaseSupport {

    /** Exclusive upper bound of {@code UTINYINT}. */
    private static final int UNSIGNED_BYTE_BOUND = 256;

    /** Exclusive upper bound of {@code USMALLINT}. */
    private static final int UNSIGNED_SHORT_BOUND = 65_536;

    /** Exclusive upper bound of {@code UINTEGER}. */
    private static final long UNSIGNED_INT_BOUND = 4_294_967_296L;

    /**
     * Bits generated for a {@code BIT} column. DuckDB's {@code BIT} is variable width and the driver
     * reports no {@code COLUMN_SIZE}, so there is nothing to derive a width from.
     */
    private static final int BIT_STRING_SIZE = 8;

    /** Creates the DuckDB support with its default configuration. */
    public DuckDBSupport() {
    }

    @Override
    protected void configure(Map<JDBCType, GeneratorFactory> registry) {

        // DuckDB has no signedness column in its metadata either, but unlike MySQL it does not append
        // UNSIGNED to the type name: it reports the unsigned type's own name against a widened JDBC
        // type. Match on the name, and leave the genuinely signed columns on the default.
        registry.put(JDBCType.SMALLINT, (column, random) -> isType(column, "UTINYINT")
                ? new ShortGenerator.Builder(random).start(0).end(UNSIGNED_BYTE_BOUND).build()
                : new ShortGenerator.Builder(random).build());

        registry.put(JDBCType.INTEGER, (column, random) -> isType(column, "USMALLINT")
                ? new IntegerGenerator.Builder(random).start(0).end(UNSIGNED_SHORT_BOUND).build()
                : new IntegerGenerator.Builder(random).build());

        registry.put(JDBCType.BIGINT, (column, random) -> isType(column, "UINTEGER")
                ? new LongGenerator.Builder(random).start(0).end(UNSIGNED_INT_BOUND).build()
                : new LongGenerator.Builder(random).build());

        // a bit string, not a single bit
        registry.put(JDBCType.BIT, (column, random) ->
                new BitStringGenerator.Builder(random).size(BIT_STRING_SIZE).build());

        // the driver has no setBlob, so a BLOB is bound as bytes; COLUMN_SIZE is not reported for
        // it either, so the generator's default length applies
        registry.put(JDBCType.BLOB, (column, random) -> new ByteGenerator.Builder(random).build());

        registry.put(JDBCType.OTHER, DuckDBSupport::other);

        registry.put(JDBCType.STRUCT, (column, random) -> {
            throw unsupported(column);
        });
    }

    /**
     * Resolves the many DuckDB types the driver reports as {@link JDBCType#OTHER}, by type name.
     */
    private static DataGenerator<?> other(Column column, RandomGenerator random) {
        String typeName = typeName(column);

        if (typeName.startsWith("ENUM(")) {
            // from the declared name, not the upper-cased one: the labels are case-sensitive values,
            // and DuckDB rejects a label it did not declare
            return enumeration(column.typeName(), random);
        }

        return switch (typeName) {
            case "UUID" -> new UUIDGenerator.Builder(random).build();
            case "JSON" -> new JsonbGenerator.Builder(random).build();
            // 128-bit integers: a long is always in range, and is what the driver binds natively
            case "HUGEINT" -> new LongGenerator.Builder(random).build();
            case "UBIGINT", "UHUGEINT" -> new LongGenerator.Builder(random).start(0).end(Long.MAX_VALUE).build();
            // the second, millisecond and nanosecond timestamps take the same bound value as TIMESTAMP
            case "TIMESTAMP_S", "TIMESTAMP_MS", "TIMESTAMP_NS" -> new SqlTimestampGenerator.Builder(random).build();
            default -> throw unsupported(column);
        };
    }

    /**
     * A generator over an {@code ENUM}'s declared labels, which DuckDB reports in the type name as
     * {@code ENUM('ok', 'sad')}. The labels are equally weighted; give the column an explicit
     * generator to skew them.
     */
    private static DataGenerator<?> enumeration(String typeName, RandomGenerator random) {
        List<String> labels = labels(typeName);

        if (labels.isEmpty()) {
            throw new UnsupportedOperationException("Data Type [" + typeName + "] declares no values");
        }

        WeightedCategoricalGenerator.Builder<String> builder = new WeightedCategoricalGenerator.Builder<>(random);
        for (String label : labels) {
            builder.add(label, 1.0);
        }
        return builder.build();
    }

    /**
     * The quoted labels of an {@code ENUM(...)} type name, in declaration order. DuckDB doubles a
     * quote inside a label, as SQL does, so {@code 'it''s'} is one label.
     */
    private static List<String> labels(String typeName) {
        List<String> labels = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inLabel = false;

        int i = typeName.indexOf('(');

        while (i >= 0 && i < typeName.length()) {
            char c = typeName.charAt(i);
            i++;

            if (c != '\'') {
                if (inLabel) {
                    current.append(c);
                }
                continue;
            }

            if (inLabel && i < typeName.length() && typeName.charAt(i) == '\'') {
                // a doubled quote is one literal quote inside the label
                current.append('\'');
                i++;
                continue;
            }

            if (inLabel) {
                labels.add(current.toString());
                current.setLength(0);
            }
            inLabel = !inLabel;
        }

        return labels;
    }

    private static boolean isType(Column column, String typeName) {
        return typeName.equals(typeName(column));
    }

    private static String typeName(Column column) {
        return column.typeName() == null ? "" : column.typeName().toUpperCase(Locale.ROOT);
    }

    private static UnsupportedOperationException unsupported(Column column) {
        return new UnsupportedOperationException("Data Type [" + column.typeName() + "] not supported");
    }
}
