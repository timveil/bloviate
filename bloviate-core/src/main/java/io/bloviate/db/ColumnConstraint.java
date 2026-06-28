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

import java.math.BigDecimal;
import java.util.List;

/**
 * A value constraint captured for a column — the machine-readable part of a {@code CHECK} constraint
 * or an {@code ENUM}/domain's allowed values — so the fill engine can generate values that satisfy it
 * instead of values a constraint would reject (issue #479).
 *
 * <p>A constraint is one of two shapes: a <strong>set of allowed values</strong> (from
 * {@code col IN (...)}, {@code = ANY (ARRAY[...])}, or an enum's labels) or a <strong>numeric
 * range</strong> (from {@code BETWEEN} / {@code >=} / {@code <=} / {@code >} / {@code <}). Forms the
 * reader can't interpret are not represented here — the engine warns and falls back to its type
 * default for those.
 *
 * @param allowedValues the permitted values (their text form), or null for a range constraint
 * @param min the lower bound, or null if unbounded below
 * @param minInclusive whether {@code min} is inclusive
 * @param max the upper bound, or null if unbounded above
 * @param maxInclusive whether {@code max} is inclusive
 * @since 2.14.0
 */
public record ColumnConstraint(List<String> allowedValues, BigDecimal min, boolean minInclusive, BigDecimal max, boolean maxInclusive) {

    /**
     * A set-of-allowed-values constraint (categorical / enum / {@code IN}).
     *
     * @param allowedValues the permitted values (their text form); defensively copied
     * @return a constraint that admits only {@code allowedValues}
     */
    public static ColumnConstraint ofValues(List<String> allowedValues) {
        return new ColumnConstraint(List.copyOf(allowedValues), null, false, null, false);
    }

    /**
     * A closed numeric range {@code [min, max]} (both inclusive).
     *
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @return a constraint that admits values in {@code [min, max]}
     */
    public static ColumnConstraint ofRange(BigDecimal min, BigDecimal max) {
        return new ColumnConstraint(null, min, true, max, true);
    }

    /**
     * True if this is a set-of-allowed-values constraint.
     *
     * @return whether allowed values are present
     */
    public boolean hasAllowedValues() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    /**
     * True if this is a numeric range with <em>both</em> bounds (the form the engine can honor).
     *
     * @return whether both {@code min} and {@code max} are present
     */
    public boolean hasBoundedRange() {
        return min != null && max != null;
    }
}
