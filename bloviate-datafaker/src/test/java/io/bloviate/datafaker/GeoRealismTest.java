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

import io.bloviate.gen.DataGenerator;
import io.bloviate.gen.IndexedDataGenerator;
import io.bloviate.util.RandomGenerators;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the issue #473 geo tuples: each row draws one real, internally consistent place from the
 * bundled reference dataset, and columns projecting it agree — reproducibly and order-independently.
 */
class GeoRealismTest {

    private static final long SEED = 42L;

    private static DataGenerator<?> projection(RowContext<Geo> context, java.util.function.Function<Geo, String> selector) {
        return context.project(selector).create(RandomGenerators.create(0));
    }

    @Test
    void referenceDatasetIsWellFormed() {
        var tuples = Places.unitedStatesTuples();
        assertTrue(tuples.size() >= 50, "expected a representative dataset but had " + tuples.size());
        for (Geo geo : tuples) {
            assertFalse(geo.city().isBlank(), "blank city");
            assertEquals(2, geo.stateAbbreviation().length(), "state abbreviation must be 2 chars: " + geo);
            assertTrue(geo.zip().matches("\\d{5}"), "zip must be 5 digits: " + geo);
            assertTrue(geo.areaCode().matches("\\d{3}"), "area code must be 3 digits: " + geo);
            assertEquals("United States", geo.country());
        }
    }

    @Test
    void eachRowIsOneRealConsistentTuple() {
        // because at() always returns a whole tuple from the dataset, city/state/zip/areaCode agree
        RowContext<Geo> place = Places.unitedStates(SEED);
        for (long row = 0; row < 500; row++) {
            assertTrue(Places.unitedStatesTuples().contains(place.at(row)),
                    "row " + row + " produced a tuple not in the reference dataset: " + place.at(row));
        }
    }

    @Test
    void columnsProjectingTheSameContextAgreeRowByRow() {
        RowContext<Geo> place = Places.unitedStates(SEED);
        DataGenerator<?> city = projection(place, Geo::city);
        DataGenerator<?> state = projection(place, Geo::stateAbbreviation);
        DataGenerator<?> zip = projection(place, Geo::zip);
        DataGenerator<?> areaCode = projection(place, Geo::areaCode);

        for (long row = 0; row < 200; row++) {
            Geo expected = place.at(row);
            assertEquals(expected.city(), city.generate(), "row " + row + " city");
            assertEquals(expected.stateAbbreviation(), state.generate(), "row " + row + " state");
            assertEquals(expected.zip(), zip.generate(), "row " + row + " zip");
            assertEquals(expected.areaCode(), areaCode.generate(), "row " + row + " area code");
        }
    }

    @Test
    void sameSeedIsReproducible() {
        RowContext<Geo> a = Places.unitedStates(SEED);
        RowContext<Geo> b = Places.unitedStates(SEED);
        for (long row = 0; row < 1_000; row++) {
            assertEquals(a.at(row), b.at(row), "same seed must reproduce the same place at row " + row);
        }
    }

    @Test
    void seekReproducesSequentialOutputForPartitioning() {
        RowContext<Geo> place = Places.unitedStates(SEED);

        DataGenerator<?> sequential = projection(place, Geo::zip);
        String[] expected = new String[60];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (String) sequential.generate();
        }
        for (int start : new int[]{0, 1, 23, 59}) {
            DataGenerator<?> seeker = projection(place, Geo::zip);
            ((IndexedDataGenerator) seeker).seek(start);
            for (int i = start; i < expected.length; i++) {
                assertEquals(expected[i], seeker.generate(), "seek(" + start + ") must match sequential zip at row " + i);
            }
        }
    }
}
