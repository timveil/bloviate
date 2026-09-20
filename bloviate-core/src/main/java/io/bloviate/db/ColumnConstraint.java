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

import io.bloviate.gen.TruncatedDateGenerator;

import java.math.BigDecimal;
import java.util.List;

/**
 * A value constraint captured for a column — the machine-readable part of a {@code CHECK} constraint
 * or an {@code ENUM}/domain's allowed values — so the fill engine can generate values that satisfy it
 * instead of values a constraint would reject (issue #479).
 *
 * <p>A constraint is one of three shapes: a <strong>set of allowed values</strong> (from
 * {@code col IN (...)}, {@code = ANY (ARRAY[...])}, or an enum's labels), a <strong>numeric
 * range</strong> (from {@code BETWEEN} / {@code >=} / {@code <=} / {@code >} / {@code <}), or a
 * <strong>date truncation</strong> &mdash; the column must be the first day of a month, quarter or
 * year (from {@code date_trunc('month', col) = col} or {@code EXTRACT(day FROM col) = 1}, issue
 * #619). Forms the reader can't interpret are not represented here — the engine warns and falls back to its type
 * default for those.
 *
 * @param allowedValues the permitted values (their text form), or null for a range constraint;
 *                      copied on construction, so the caller's list can be mutated afterwards
 *                      without affecting the constraint
 * @param min the lower bound, or null if unbounded below
 * @param minInclusive whether {@code min} is inclusive
 * @param max the upper bound, or null if unbounded above
 * @param maxInclusive whether {@code max} is inclusive
 * @param dateTruncation for a date/timestamp column that must hold the first day of a period, that
 *                       period; null otherwise (added in 3.4.0)
 * @since 2.14.0
 */
public record ColumnConstraint(List<String> allowedValues, BigDecimal min, boolean minInclusive, BigDecimal max, boolean maxInclusive,
                               TruncatedDateGenerator.Unit dateTruncation) {

    /**
     * The constructor as it was before date truncation was added (3.4.0): a set-of-values or numeric
     * range constraint, with no {@link #dateTruncation()}.
     *
     * @param allowedValues the permitted values (their text form), or null for a range constraint
     * @param min the lower bound, or null if unbounded below
     * @param minInclusive whether {@code min} is inclusive
     * @param max the upper bound, or null if unbounded above
     * @param maxInclusive whether {@code max} is inclusive
     */
    public ColumnConstraint(List<String> allowedValues, BigDecimal min, boolean minInclusive, BigDecimal max, boolean maxInclusive) {
        this(allowedValues, min, minInclusive, max, maxInclusive, null);
    }

    /**
     * Copies {@code allowedValues} so the record is deeply immutable — constraint metadata is
     * cached per fill and read from worker threads during parallel fills.
     *
     * <p>Null is preserved rather than normalised to an empty list: it is the documented marker
     * for "this is a range constraint, not a set constraint", and {@link #hasAllowedValues()}
     * depends on the distinction.
     */
    public ColumnConstraint {
        allowedValues = allowedValues == null ? null : List.copyOf(allowedValues);
    }

    /**
     * A set-of-allowed-values constraint (categorical / enum / {@code IN}).
     *
     * @param allowedValues the permitted values (their text form); defensively copied
     * @return a constraint that admits only {@code allowedValues}
     */
    public static ColumnConstraint ofValues(List<String> allowedValues) {
        // the canonical constructor copies; copying here too would only allocate twice
        return new ColumnConstraint(allowedValues, null, false, null, false, null);
    }

    /**
     * A first-day-of-period constraint: the column must hold the first day of a month, quarter or
     * year ({@code date_trunc('month', col) = col}).
     *
     * @param unit the period whose first day the column must hold
     * @return a constraint that admits only first days of {@code unit}
     * @since 3.4.0
     */
    public static ColumnConstraint ofDateTruncation(TruncatedDateGenerator.Unit unit) {
        return new ColumnConstraint(null, null, false, null, false, unit);
    }

    /**
     * A closed numeric range {@code [min, max]} (both inclusive).
     *
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @return a constraint that admits values in {@code [min, max]}
     */
    public static ColumnConstraint ofRange(BigDecimal min, BigDecimal max) {
        return new ColumnConstraint(null, min, true, max, true, null);
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

    /**
     * True if the column must hold the first day of a period (month, quarter or year).
     *
     * @return whether a date truncation is present
     * @since 3.4.0
     */
    public boolean hasDateTruncation() {
        return dateTruncation != null;
    }
}
