package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class DoubleGenerator implements DataGenerator<Double> {

    private final double startInclusive;
    private final double endExclusive;

    @Override
    public Double generate() {
        return RandomUtils.nextDouble(startInclusive, endExclusive);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }


    public static class Builder {

        private double startInclusive = 0;
        private double endExclusive = Double.MAX_VALUE;

        public Builder start(double start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(double end) {
            this.endExclusive = end;
            return this;
        }

        public DoubleGenerator build() {
            return new DoubleGenerator(this);
        }
    }

    private DoubleGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
