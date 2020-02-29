package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class LongGenerator implements DataGenerator<Long> {

    private final long startInclusive;
    private final long endExclusive;

    @Override
    public Long generate() {
        return RandomUtils.nextLong(startInclusive, endExclusive);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        private long startInclusive = 0;
        private long endExclusive = Long.MAX_VALUE;

        public Builder start(long start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(long end) {
            this.endExclusive = end;
            return this;
        }

        public LongGenerator build() {
            return new LongGenerator(this);
        }
    }

    private LongGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
