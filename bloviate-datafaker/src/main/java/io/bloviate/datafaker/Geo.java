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

/**
 * A real, internally consistent geographic tuple for one row: the {@code city}, {@code state}
 * (and {@code stateAbbreviation}), {@code zip}, {@code areaCode}, and {@code country} all belong
 * together (a real city in its real state with a valid local zip and area code) — not "Springfield,
 * WY 90210". Drawn from a bundled reference dataset by {@link Places}; this is what Datafaker's
 * independent address parts cannot provide.
 *
 * @since 2.12.0
 * @see Places
 * @see RowContext
 */
public record Geo(String city, String stateAbbreviation, String state, String zip, String areaCode, String country) {
}
