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

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A signed distance from an anchor instant, written the way a person would: {@code 90d}, {@code -30d},
 * {@code +7d}, {@code 12w}, {@code 6M}, {@code 1y}, {@code 36h}. It is the building block of a
 * {@link RelativeWindow}, and it is what the declarative configuration and the command line parse.
 *
 * <p>The syntax is an optional sign ({@code +} or {@code -}, default {@code +}), one or more digits and
 * exactly one unit letter. Units are case-sensitive:
 *
 * <table class="striped">
 * <caption>Offset units</caption>
 * <thead><tr><th>Unit</th><th>Meaning</th><th>Arithmetic</th></tr></thead>
 * <tbody>
 * <tr><td>{@code h}</td><td>hours</td><td>exact 3,600 seconds</td></tr>
 * <tr><td>{@code d}</td><td>days</td><td>exact 24 hours</td></tr>
 * <tr><td>{@code w}</td><td>weeks</td><td>exact 7 &times; 24 hours</td></tr>
 * <tr><td>{@code M}</td><td>calendar months</td><td>calendar arithmetic in UTC; the day of month is
 * clamped to the target month's length, so {@code 2026-03-31} minus {@code 1M} is {@code 2026-02-28}</td></tr>
 * <tr><td>{@code y}</td><td>calendar years</td><td>calendar arithmetic in UTC; {@code 2024-02-29} plus
 * {@code 1y} is {@code 2025-02-28}</td></tr>
 * </tbody>
 * </table>
 *
 * <p>Everything is evaluated in UTC, so daylight-saving transitions never lengthen or shorten a day, and
 * the same offset applied to the same instant gives the same result on every JVM and in every time zone.
 * There is deliberately no minute or second unit (a lower-case {@code m} would be too easily mistaken for
 * the month {@code M}); sub-hour precision is not what a relative window is for.
 *
 * @param amount the signed number of {@code unit}s
 * @param unit   the unit
 * @see RelativeWindow
 * @since 3.7.0
 */
public record RelativeOffset(long amount, Unit unit) {

    private static final Pattern SYNTAX = Pattern.compile("([+-])?(\\d+)(\\p{L})");

    /** The unit of a {@link RelativeOffset}. */
    public enum Unit {

        /** Exact hours. */
        HOURS('h', Duration.ofHours(1)),

        /** Exact 24-hour days (UTC has no daylight saving). */
        DAYS('d', Duration.ofDays(1)),

        /** Exact 7-day weeks. */
        WEEKS('w', Duration.ofDays(7)),

        /** Calendar months. */
        MONTHS('M', null),

        /** Calendar years. */
        YEARS('y', null);

        private final char symbol;
        private final Duration length;

        Unit(char symbol, Duration length) {
            this.symbol = symbol;
            this.length = length;
        }

        /**
         * The letter that denotes this unit in the text form.
         *
         * @return {@code h}, {@code d}, {@code w}, {@code M} or {@code y}
         */
        public char symbol() {
            return symbol;
        }

        /** True for units whose length does not depend on the date they are applied to. */
        boolean isFixedLength() {
            return length != null;
        }

        static Unit ofSymbol(char symbol) {
            for (Unit unit : values()) {
                if (unit.symbol == symbol) {
                    return unit;
                }
            }
            return null;
        }
    }

    /**
     * Creates an offset.
     *
     * @param amount the signed number of units
     * @param unit   the unit
     */
    public RelativeOffset {
        Objects.requireNonNull(unit, "unit must not be null");
    }

    /**
     * Parses the text form of an offset, for example {@code 90d}, {@code -30d}, {@code +7d}, {@code 6M}.
     * Surrounding whitespace is ignored; whitespace inside the offset is not.
     *
     * @param text the offset text
     * @return the parsed offset
     * @throws NullPointerException     if {@code text} is null
     * @throws IllegalArgumentException if {@code text} is not an optional sign, digits and one of the
     *                                  units {@code h d w M y}, or the amount is too large
     */
    public static RelativeOffset parse(String text) {
        Objects.requireNonNull(text, "offset must not be null");
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("offset must not be blank; expected a number and a unit such as 90d, -30d, 6M");
        }
        Matcher matcher = SYNTAX.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid offset \"" + text + "\": expected an optional sign, digits and "
                    + "one unit (h, d, w, M, y), such as 90d, -30d, +7d, 6M");
        }
        Unit unit = Unit.ofSymbol(matcher.group(3).charAt(0));
        if (unit == null) {
            throw new IllegalArgumentException("invalid offset \"" + text + "\": unknown unit '" + matcher.group(3)
                    + "'; units are h (hours), d (days), w (weeks), M (months), y (years), and are case-sensitive");
        }
        long amount;
        try {
            amount = Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid offset \"" + text + "\": the amount is too large", e);
        }
        return new RelativeOffset("-".equals(matcher.group(1)) ? -amount : amount, unit);
    }

    /**
     * The instant this offset away from {@code anchor}: later for a positive offset, earlier for a
     * negative one. Months and years are added as calendar months and years in UTC; hours, days and
     * weeks are exact durations.
     *
     * @param anchor the instant to measure from
     * @return {@code anchor} moved by this offset
     * @throws IllegalArgumentException if the result is outside the range an {@link Instant} can hold
     */
    public Instant addTo(Instant anchor) {
        Objects.requireNonNull(anchor, "anchor must not be null");
        try {
            if (unit.isFixedLength()) {
                return anchor.plus(unit.length.multipliedBy(amount));
            }
            var utc = anchor.atZone(ZoneOffset.UTC);
            return (unit == Unit.MONTHS ? utc.plusMonths(amount) : utc.plusYears(amount)).toInstant();
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("offset " + this + " from " + anchor + " is outside the supported range", e);
        }
    }

    /**
     * The same distance in the opposite direction.
     *
     * @return this offset with its sign flipped
     */
    public RelativeOffset negate() {
        return new RelativeOffset(-amount, unit);
    }

    /** True if the offset is before its anchor. */
    boolean isNegative() {
        return amount < 0;
    }

    /** True if the offset is the anchor itself. */
    boolean isZero() {
        return amount == 0;
    }

    /** True if the offset's length does not depend on the anchor it is applied to (hours, days, weeks). */
    boolean isFixedLength() {
        return unit.isFixedLength();
    }

    /**
     * The text form, which {@link #parse(String)} reads back: the sign only when negative, for example
     * {@code -30d} or {@code 6M}.
     *
     * @return the offset as text
     */
    @Override
    public String toString() {
        return amount + String.valueOf(unit.symbol);
    }
}
