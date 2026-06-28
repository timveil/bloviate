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

package io.bloviate.datafaker;

import io.bloviate.util.Mixers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link RowContext} of internally consistent {@link Geo} tuples for correlated geographic
 * columns (issue #473). Unlike Datafaker's independent address parts (which yield "Springfield, WY
 * 90210"), each row draws one <em>real</em> tuple from a bundled reference dataset, so the row's
 * {@code city}, {@code state}, {@code zip}, and {@code area_code} columns agree.
 *
 * <pre>{@code
 * RowContext<Geo> place = Places.unitedStates(seed);
 * Set.of(
 *     new ColumnConfiguration("city",      place.project(Geo::city)),
 *     new ColumnConfiguration("state",     place.project(Geo::stateAbbreviation)),
 *     new ColumnConfiguration("zip",       place.project(Geo::zip)),
 *     new ColumnConfiguration("area_code", place.project(Geo::areaCode)));
 * }</pre>
 *
 * <p>The tuple chosen for a row is a pure function of {@code (seed, rowIndex)}, so the data is
 * reproducible and order-independent under parallel/partitioned fills. The bundled dataset is a
 * representative sample of US cities, not exhaustive; tuples repeat across rows (geography is not
 * unique), so do not route {@code UNIQUE} columns through it.
 *
 * @since 2.12.0
 * @see RowContext
 * @see Geo
 */
public final class Places {

    private static final String COUNTRY = "United States";
    private static final List<Geo> UNITED_STATES = load("/io/bloviate/datafaker/geo-us.csv");

    private Places() {
    }

    /**
     * A context of consistent US {@link Geo} tuples.
     *
     * @param seed the group seed; vary it for a different (still reproducible) sequence of places
     * @return a row context wireable via {@link RowContext#project}
     */
    public static RowContext<Geo> unitedStates(long seed) {
        List<Geo> tuples = UNITED_STATES;
        int size = tuples.size();
        return new RowContext<>(rowIndex -> tuples.get(Math.floorMod(Mixers.splitmix64(seed + rowIndex), size)));
    }

    /** The loaded reference tuples (package-private; for tests). */
    static List<Geo> unitedStatesTuples() {
        return UNITED_STATES;
    }

    private static List<Geo> load(String resource) {
        List<Geo> tuples = new ArrayList<>();
        try (InputStream in = Places.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing geo reference dataset: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("city,")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length < 5) {
                        continue;
                    }
                    tuples.add(new Geo(fields[0], fields[1], fields[2], fields[3], fields[4], COUNTRY));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load geo reference dataset: " + resource, e);
        }
        if (tuples.isEmpty()) {
            throw new IllegalStateException("geo reference dataset is empty: " + resource);
        }
        return List.copyOf(tuples);
    }
}
