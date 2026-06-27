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

package io.bloviate.util;

import org.apache.commons.lang3.Validate;

import java.util.random.RandomGenerator;

/**
 * Seeded numeric and string helpers built directly on a {@link RandomGenerator}.
 *
 * <p>The numeric range helpers are adapted from {@code org.apache.commons.lang3.RandomUtils}.
 * The string helpers are reimplemented here (rather than delegating to
 * {@code org.apache.commons.lang3.RandomStringUtils}) because that class accepts only a
 * {@link java.util.Random}, whereas the engine now drives generation from a
 * {@link RandomGenerator} ({@value RandomGenerators#ALGORITHM}). For the alphabetic/numeric
 * cases the implementation draws directly from a fixed character pool &mdash; the same set of
 * characters {@code RandomStringUtils} would yield for those flags &mdash; which avoids the
 * draw-and-reject loop entirely. The general {@code [start, end)} code-point path is retained for
 * completeness and mirrors the classic {@code RandomStringUtils.random(...)} rejection algorithm.
 */

public record SeededRandomUtils(RandomGenerator random) {

    /** Pool for {@code numbers}-only requests: the ASCII digits {@code RandomStringUtils} keeps. */
    private static final char[] DIGITS = "0123456789".toCharArray();

    /** Pool for {@code letters}-only requests: the ASCII letters {@code RandomStringUtils} keeps. */
    private static final char[] LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /** Pool for {@code letters && numbers} requests. */
    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    public String randomNumeric(int minLengthInclusive, int maxLengthExclusive) {
        return randomNumeric(nextInt(minLengthInclusive, maxLengthExclusive));
    }

    public String randomNumeric(final int count) {
        return random(count, false, true);
    }

    public String random(final int count, final boolean letters, final boolean numbers) {
        if (count < 0) {
            throw new IllegalArgumentException("Requested random string length " + count + " is less than 0.");
        }
        if (count == 0) {
            return "";
        }

        // fast path for the only cases the engine uses: pick from a fixed pool with no rejection
        char[] pool = null;
        if (letters && numbers) {
            pool = ALPHANUMERIC;
        } else if (letters) {
            pool = LETTERS;
        } else if (numbers) {
            pool = DIGITS;
        }
        if (pool != null) {
            final char[] out = new char[count];
            for (int i = 0; i < count; i++) {
                out[i] = pool[random.nextInt(pool.length)];
            }
            return new String(out);
        }

        // !letters && !numbers: any code point — fall back to the general range path
        return random(count, 0, 0, false, false);
    }

    /**
     * Generates a random string of the requested length, drawing code points from the
     * {@code [start, end)} range and keeping only letters and/or digits as requested. This mirrors
     * the classic {@code RandomStringUtils.random(count, start, end, letters, numbers, null, random)}
     * rejection algorithm, sourcing randomness from this record's {@link RandomGenerator}. The
     * common alphabetic/numeric requests are served by {@link #random(int, boolean, boolean)}'s
     * pool fast path instead.
     *
     * @param count   the length of the string to create
     * @param start   the inclusive lower bound of code points to draw from (0 selects a default)
     * @param end     the exclusive upper bound of code points to draw from (0 selects a default)
     * @param letters whether letters are allowed
     * @param numbers whether digits are allowed
     * @return the generated random string
     */
    public String random(final int count, final int start, final int end, final boolean letters, final boolean numbers) {
        if (count == 0) {
            return "";
        }
        if (count < 0) {
            throw new IllegalArgumentException("Requested random string length " + count + " is less than 0.");
        }

        int lower = start;
        int upper = end;
        if (lower == 0 && upper == 0) {
            if (!letters && !numbers) {
                upper = Character.MAX_CODE_POINT;
            } else {
                upper = 'z' + 1;
                lower = ' ';
            }
        } else if (upper <= lower) {
            throw new IllegalArgumentException("Parameter end (" + upper + ") must be greater than start (" + lower + ")");
        } else if (lower < 0) {
            throw new IllegalArgumentException("Character positions MUST be >= 0");
        }

        if (upper > Character.MAX_CODE_POINT) {
            upper = Character.MAX_CODE_POINT;
        }

        final StringBuilder builder = new StringBuilder(count);
        final int gap = upper - lower;

        int remaining = count;
        while (remaining-- != 0) {
            final int codePoint = random.nextInt(gap) + lower;
            switch (Character.getType(codePoint)) {
                case Character.UNASSIGNED, Character.PRIVATE_USE, Character.SURROGATE -> {
                    remaining++;
                    continue;
                }
            }

            final int numberOfChars = Character.charCount(codePoint);
            if (remaining == 0 && numberOfChars > 1) {
                remaining++;
                continue;
            }

            if (letters && Character.isLetter(codePoint)
                    || numbers && Character.isDigit(codePoint)
                    || !letters && !numbers) {
                builder.appendCodePoint(codePoint);
                if (numberOfChars == 2) {
                    remaining--;
                }
            } else {
                remaining++;
            }
        }
        return builder.toString();
    }

    public String randomAlphabetic(final int count) {
        return random(count, true, false);
    }

    public int nextInt(final int startInclusive, final int endExclusive) {
        Validate.isTrue(endExclusive >= startInclusive, "Start value must be smaller or equal to end value.");
        Validate.isTrue(startInclusive >= 0, "Both range values must be non-negative.");

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + random.nextInt(endExclusive - startInclusive);
    }

    public double nextDouble(final double startInclusive, final double endExclusive) {
        Validate.isTrue(endExclusive >= startInclusive, "Start value must be smaller or equal to end value.");
        Validate.isTrue(startInclusive >= 0, "Both range values must be non-negative.");

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + ((endExclusive - startInclusive) * random.nextDouble());
    }

    public float nextFloat(final float startInclusive, final float endExclusive) {
        Validate.isTrue(endExclusive >= startInclusive, "Start value must be smaller or equal to end value.");
        Validate.isTrue(startInclusive >= 0, "Both range values must be non-negative.");

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        return startInclusive + ((endExclusive - startInclusive) * random.nextFloat());
    }

    public long nextLong(final long startInclusive, final long endExclusive) {
        Validate.isTrue(endExclusive >= startInclusive, "Start value must be smaller or equal to end value.");
        Validate.isTrue(startInclusive >= 0, "Both range values must be non-negative.");

        if (startInclusive == endExclusive) {
            return startInclusive;
        }

        // generate the long directly rather than via double; routing through double
        // would quantize values above 2^53 and make the top of wide ranges unreachable
        return random.nextLong(startInclusive, endExclusive);
    }

}
