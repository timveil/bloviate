package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class FloatGenerator implements DataGenerator<Float> {

    private final float startInclusive;
    private final float endExclusive;

    @Override
    public Float generate() {
        return RandomUtils.nextFloat(startInclusive, endExclusive);
    }

    public static class Builder {

        private float startInclusive = 0;
        private float endExclusive = Float.MAX_VALUE;

        public Builder start(float start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(float end) {
            this.endExclusive = end;
            return this;
        }

        public FloatGenerator build() {
            return new FloatGenerator(this);
        }
    }

    private FloatGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
