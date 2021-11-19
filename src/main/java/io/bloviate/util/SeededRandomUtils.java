package io.bloviate.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.Validate;

import java.util.Random;

/**
 * Borrowed from org.apache.commons.lang3.RandomUtils and org.apache.commons.lang3.RandomStringUtils
 */

public class SeededRandomUtils {

    private final Random random;

    public SeededRandomUtils(Random random) {
        this.random = random;
    }

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
