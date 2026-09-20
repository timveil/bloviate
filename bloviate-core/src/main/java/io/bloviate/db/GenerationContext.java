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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What a fill knows that a generator may depend on besides its seed: today, the {@code asOf} anchor
 * that {@link io.bloviate.gen.RelativeWindow relative date windows} are measured from. One context is
 * created per fill and shared by every table, intra-table partition and worker thread, so every
 * generator that reads the anchor reads the same instant.
 *
 * <p>Generator factories receive it through
 * {@link ColumnGeneratorFactory#create(java.util.random.RandomGenerator, GenerationContext)} and
 * {@link io.bloviate.ext.GeneratorFactory#create(Column, java.util.random.RandomGenerator, GenerationContext)}.
 * A factory that ignores it (every factory written before it existed) behaves exactly as before.
 *
 * <h2>Reproducibility</h2>
 * Wall-clock time is not an input to generation. The anchor is the one place a clock can enter, and it
 * does so explicitly:
 * <ul>
 *   <li>{@linkplain #pinned(Instant) pinned}: the caller chose the instant. The same seed and the same
 *       pinned anchor produce identical output on every run and every JDK.</li>
 *   <li>{@linkplain #unpinned() unpinned}: none was chosen, so the anchor is read once from the clock,
 *       when the fill starts, and truncated to the start of the current UTC day. Every table and worker of
 *       that fill shares it, and it is logged once at INFO the first time a generator reads it. Output is
 *       then reproducible only by pinning the value that was logged.</li>
 * </ul>
 *
 * @see io.bloviate.gen.RelativeWindow
 * @since 3.7.0
 */
public final class GenerationContext {

    private static final Logger logger = LoggerFactory.getLogger(GenerationContext.class);

    private final Instant asOf;
    private final boolean pinned;
    private final AtomicBoolean announced = new AtomicBoolean();

    private GenerationContext(Instant asOf, boolean pinned) {
        this.asOf = asOf;
        this.pinned = pinned;
        // a pinned anchor is the caller's own choice; only a derived one needs announcing
        this.announced.set(pinned);
    }

    /**
     * A context whose anchor is the given instant, used as is (not truncated).
     *
     * @param asOf the anchor
     * @return the context
     * @throws NullPointerException if {@code asOf} is null
     */
    public static GenerationContext pinned(Instant asOf) {
        return new GenerationContext(Objects.requireNonNull(asOf, "asOf must not be null"), true);
    }

    /**
     * A context whose anchor is the start of the current UTC day (00:00Z), read from the system clock
     * now. Reproducing the output later requires pinning the anchor this resolved to.
     *
     * @return the context
     */
    public static GenerationContext unpinned() {
        return unpinned(Clock.systemUTC());
    }

    /** As {@link #unpinned()}, reading {@code clock}: the seam that lets a test avoid the wall clock. */
    static GenerationContext unpinned(Clock clock) {
        return new GenerationContext(clock.instant().truncatedTo(ChronoUnit.DAYS), false);
    }

    /**
     * The anchor that relative windows are measured from. When it was not {@linkplain #pinned pinned}
     * this logs, once per context, the instant it resolved to.
     *
     * @return the anchor instant
     */
    public Instant asOf() {
        if (announced.compareAndSet(false, true)) {
            logger.info("resolved asOf {} (start of the current UTC day, as none was pinned); pin it with "
                    + "asOf(...) for reproducible output", asOf);
        }
        return asOf;
    }

    /**
     * The anchor without announcing it: for the engine, which reports it itself.
     */
    Instant peekAsOf() {
        return asOf;
    }

    /**
     * Whether the caller chose the anchor ({@code true}) or it was derived from the clock.
     *
     * @return true if pinned
     */
    public boolean isPinned() {
        return pinned;
    }

    @Override
    public String toString() {
        return "GenerationContext[asOf=" + asOf + (pinned ? ", pinned" : ", from clock") + "]";
    }
}
