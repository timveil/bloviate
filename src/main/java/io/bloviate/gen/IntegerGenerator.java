package io.bloviate.gen;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

public class IntegerGenerator implements DataGenerator<Integer> {

    private final int startInclusive;
    private final int endExclusive;

    @Override
    public Integer generate() {
        return RandomUtils.nextInt(startInclusive, endExclusive);
    }

    public static class Builder {

        private int startInclusive = 0;
        private int endExclusive = Integer.MAX_VALUE;

        public Builder start(int start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(int end) {
            this.endExclusive = end;
            return this;
        }

        public IntegerGenerator build() {
            return new IntegerGenerator(this);
        }
    }

    private IntegerGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
