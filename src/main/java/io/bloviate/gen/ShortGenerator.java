package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class ShortGenerator implements DataGenerator<Short> {

    private final int startInclusive;
    private final int endExclusive;

    @Override
    public Short generate() {
        return (short) RandomUtils.nextInt(startInclusive, endExclusive);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        private int startInclusive = 0;
        private int endExclusive = Short.MAX_VALUE;

        public Builder start(int start) {
            if  (start < Short.MIN_VALUE) {
                throw new IllegalArgumentException("invalid start value.  Less than Short.MIN_VALUE.");
            }
            this.startInclusive = start;
            return this;
        }

        public Builder end(int end) {
            if  (end > Short.MAX_VALUE) {
                throw new IllegalArgumentException("invalid end value.  Greater than Short.MAX_VALUE.");
            }

            this.endExclusive = end;
            return this;
        }

        public ShortGenerator build() {
            return new ShortGenerator(this);
        }
    }

    private ShortGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
