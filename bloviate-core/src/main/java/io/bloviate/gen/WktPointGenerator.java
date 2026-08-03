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

package io.bloviate.gen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Generator for geospatial point columns. Produces a
 * <a href="https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry">Well-Known
 * Text</a> point literal &mdash; {@code POINT(longitude latitude)} &mdash; as a {@link String}.
 *
 * <p>Coordinates are drawn uniformly over the whole globe: longitude in {@code [-180, 180]},
 * latitude in {@code [-90, 90]}, rounded to a configurable number of decimal places. Six places is
 * roughly 0.1 m at the equator, which is finer than any generated data needs to be and keeps the
 * literal short.
 *
 * <p>WKT is the interchange form every geospatial engine accepts, but usually not as a directly
 * bound parameter &mdash; BigQuery needs {@code ST_GEOGFROMTEXT(?)} and PostGIS
 * {@code ST_GeomFromText(?)}. Pair this with {@link SqlExpressionGenerator} to supply that wrapping
 * rather than binding the text straight into a geography column, which the server will reject.
 *
 * <p>Longitude precedes latitude, per WKT (and unlike the "lat, long" convention of mapping UIs).
 *
 * @since 3.2.0
 */
public class WktPointGenerator extends AbstractDataGenerator<String> {

    private static final int MAX_LONGITUDE = 180;
    private static final int MAX_LATITUDE = 90;

    private final int scale;

    @Override
    public String generate() {
        // draw order (longitude, then latitude) is part of the reproducibility contract
        String longitude = coordinate(MAX_LONGITUDE);
        String latitude = coordinate(MAX_LATITUDE);

        return "POINT(" + longitude + " " + latitude + ")";
    }

    private String coordinate(int bound) {
        // drawn over [0, 2*bound] and shifted, because the shared random utility rejects a negative
        // range; one draw either way, so the seeded sequence is unaffected
        double value = randomUtils.nextDouble(0, 2.0 * bound) - bound;
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /** Fluent builder for {@link WktPointGenerator}. */
    public static class Builder extends AbstractBuilder<String> {

        private int scale = 6;

        /**
         * Creates a builder backed by the given seeded random source.
         *
         * @param random the random source used to draw generated values
         */
        public Builder(RandomGenerator random) {
            super(random);
        }

        /**
         * Sets how many decimal places each coordinate carries. Defaults to {@code 6}, about 0.1 m
         * of resolution at the equator.
         *
         * @param scale the number of decimal places; must not be negative
         * @return this builder, for chaining
         * @throws IllegalArgumentException if {@code scale} is negative
         */
        public Builder scale(int scale) {
            if (scale < 0) {
                throw new IllegalArgumentException(
                        String.format(Locale.ROOT, "scale cannot be negative: %d", scale));
            }
            this.scale = scale;
            return this;
        }

        @Override
        public WktPointGenerator build() {
            return new WktPointGenerator(this);
        }
    }

    private WktPointGenerator(Builder builder) {
        super(builder.random);
        this.scale = builder.scale;
    }
}
