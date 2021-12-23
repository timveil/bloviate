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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.Validate;

import java.util.Random;

/**
 * Borrowed from org.apache.commons.lang3.RandomUtils and org.apache.commons.lang3.RandomStringUtils
 */

public record SeededRandomUtils(Random random) {

    public String randomNumeric(int minLengthInclusive, int maxLengthExclusive) {
        return randomNumeric(nextInt(minLengthInclusive, maxLengthExclusive));
    }

    public String randomNumeric(final int count) {
        return random(count, false, true);
    }

    public String random(final int count, final boolean letters, final boolean numbers) {
        return random(count, 0, 0, letters, numbers);
    }

    public String random(final int count, final int start, final int end, final boolean letters, final boolean numbers) {
        return RandomStringUtils.random(count, start, end, letters, numbers, null, random);
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

        return (long) nextDouble(startInclusive, endExclusive);
    }

}
