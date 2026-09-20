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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A span of time expressed relative to an anchor (the {@code asOf} instant of a fill) instead of as
 * absolute dates: "within the last 90 days", "from 30 days ago to a week from now". Resolving it against
 * an anchor with {@link #resolve(Instant)} gives the concrete {@link Resolved} bounds the temporal
 * generators take.
 *
 * <pre>{@code
 * RelativeWindow.withinLast("90d");           // [asOf - 90d, asOf)
 * RelativeWindow.between("-30d", "+7d");      // [asOf - 30d, asOf + 7d)
 * RelativeWindow.withinLast("6M");            // [asOf - 6 calendar months, asOf)
 * }</pre>
 *
 * <p><strong>Boundary semantics.</strong> A window is half-open: the start is inclusive and the end is
 * exclusive, the convention of {@link SqlTimestampGenerator}, {@link SqlDateGenerator},
 * {@link DateGenerator}, {@link InstantGenerator} and {@link TruncatedDateGenerator}. So
 * {@code withinLast("90d")} yields instants from {@code asOf - 90d} up to, but excluding, {@code asOf}
 * itself; a value in the window is always strictly before the anchor. Use {@code between("-90d", "1d")}
 * for a window that includes the anchor's whole day. The offsets are those of {@link RelativeOffset}
 * (months and years by the calendar, everything in UTC).
 *
 * <p>The window says nothing about <em>when</em> the anchor is; that is decided once per fill (see
 * {@code DatabaseFiller.Builder#asOf}). Given the same anchor a window always resolves to the same
 * bounds, which is what keeps a fill reproducible.
 *
 * @param start the inclusive start, relative to the anchor
 * @param end   the exclusive end, relative to the anchor
 * @see RelativeOffset
 * @since 3.7.0
 */
public record RelativeWindow(RelativeOffset start, RelativeOffset end) {

    /**
     * Creates a window between two offsets from the anchor.
     *
     * @param start the inclusive start
     * @param end   the exclusive end
     * @throws NullPointerException     if either offset is null
     * @throws IllegalArgumentException if both offsets are fixed-length (hours, days, weeks) and
     *                                  {@code end} is not after {@code start}. Offsets involving months or
     *                                  years depend on the anchor, so that case is checked by
     *                                  {@link #resolve(Instant)}.
     */
    public RelativeWindow {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (start.isFixedLength() && end.isFixedLength()
                && !start.addTo(Instant.EPOCH).isBefore(end.addTo(Instant.EPOCH))) {
            throw new IllegalArgumentException("the window's end (" + end + ") must be after its start (" + start + ")");
        }
    }

    /**
     * The window that reaches back a span from the anchor: {@code [asOf - span, asOf)}.
     *
     * @param span how far back the window reaches; must be positive
     * @return the window
     * @throws NullPointerException     if {@code span} is null
     * @throws IllegalArgumentException if {@code span} is zero or negative
     */
    public static RelativeWindow withinLast(RelativeOffset span) {
        Objects.requireNonNull(span, "span must not be null");
        if (span.isZero() || span.isNegative()) {
            throw new IllegalArgumentException("withinLast needs a positive span such as 90d, but was " + span
                    + (span.isZero() ? " (an empty window)" : "; use between(...) for a window that is not the past"));
        }
        return new RelativeWindow(span.negate(), new RelativeOffset(0, span.unit()));
    }

    /**
     * Parses and builds {@link #withinLast(RelativeOffset)}, the declarative {@code withinLast: 90d}.
     *
     * @param span the span text, see {@link RelativeOffset#parse(String)}
     * @return the window
     * @throws IllegalArgumentException if {@code span} is not a valid offset, or is zero or negative
     */
    public static RelativeWindow withinLast(String span) {
        return withinLast(RelativeOffset.parse(span));
    }

    /**
     * The window from one offset to another, both measured from the anchor: {@code [asOf + start, asOf + end)}.
     *
     * @param start the inclusive start, for example {@code -30d}
     * @param end   the exclusive end, for example {@code +7d}
     * @return the window
     * @throws IllegalArgumentException if {@code end} is not after {@code start}, when that can be told
     *                                  without an anchor
     */
    public static RelativeWindow between(RelativeOffset start, RelativeOffset end) {
        return new RelativeWindow(start, end);
    }

    /**
     * Parses and builds {@link #between(RelativeOffset, RelativeOffset)}, the declarative
     * {@code between: [-30d, +7d]}.
     *
     * @param start the start offset text, see {@link RelativeOffset#parse(String)}
     * @param end   the end offset text
     * @return the window
     * @throws IllegalArgumentException if either offset is invalid or {@code end} is not after {@code start}
     */
    public static RelativeWindow between(String start, String end) {
        return between(RelativeOffset.parse(start), RelativeOffset.parse(end));
    }

    /**
     * The concrete bounds of this window for the given anchor.
     *
     * @param asOf the anchor instant
     * @return {@code [asOf + start, asOf + end)}
     * @throws NullPointerException     if {@code asOf} is null
     * @throws IllegalArgumentException if a bound falls outside the range an {@link Instant} can hold, or
     *                                  (for a window involving months or years) the end is not after the
     *                                  start for this anchor
     */
    public Resolved resolve(Instant asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        Instant resolvedStart = start.addTo(asOf);
        Instant resolvedEnd = end.addTo(asOf);
        if (!resolvedStart.isBefore(resolvedEnd)) {
            throw new IllegalArgumentException("the window " + this + " is empty for asOf " + asOf
                    + ": its end " + resolvedEnd + " is not after its start " + resolvedStart);
        }
        return new Resolved(resolvedStart, resolvedEnd);
    }

    /**
     * The window as {@code [start, end)} offsets, for example {@code [-90d, 0d)}.
     *
     * @return the window as text
     */
    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }

    /**
     * A {@link RelativeWindow} resolved against an anchor: the concrete half-open range
     * {@code [start, end)}, start inclusive and end exclusive. This is what a generator builder's
     * {@code window(...)} takes.
     *
     * @param start the inclusive start
     * @param end   the exclusive end; always after {@code start}
     * @since 3.7.0
     */
    public record Resolved(Instant start, Instant end) {

        /**
         * Creates a resolved window.
         *
         * @param start the inclusive start
         * @param end   the exclusive end
         * @throws NullPointerException     if either bound is null
         * @throws IllegalArgumentException if {@code end} is not after {@code start}
         */
        public Resolved {
            Objects.requireNonNull(start, "start must not be null");
            Objects.requireNonNull(end, "end must not be null");
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("end (" + end + ") must be after start (" + start + ")");
            }
        }

        /**
         * The start as a {@link Timestamp}.
         *
         * @return the inclusive start
         */
        public Timestamp startTimestamp() {
            return Timestamp.from(start);
        }

        /**
         * The end as a {@link Timestamp}.
         *
         * @return the exclusive end
         */
        public Timestamp endTimestamp() {
            return Timestamp.from(end);
        }

        /**
         * The first date whose midnight (UTC) is not before the start, that is the start date, rounded
         * up when the start has a time of day. A date {@code d} is in the window exactly when
         * {@code startDate() <= d < endDate()}.
         *
         * @return the inclusive start date
         */
        public LocalDate startDate() {
            return ceilingDate(start);
        }

        /**
         * The first date whose midnight (UTC) is not before the end, that is the end date, rounded up
         * when the end has a time of day. Exclusive.
         *
         * @return the exclusive end date
         */
        public LocalDate endDate() {
            return ceilingDate(end);
        }

        private static LocalDate ceilingDate(Instant instant) {
            var utc = instant.atZone(ZoneOffset.UTC);
            LocalDate date = utc.toLocalDate();
            return utc.toLocalTime().equals(LocalTime.MIDNIGHT) ? date : date.plusDays(1);
        }
    }
}
