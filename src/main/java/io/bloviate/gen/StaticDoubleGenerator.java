package io.bloviate.gen;

import java.util.Random;

public class StaticDoubleGenerator extends AbstractDataGenerator<Double> {

    private final Double value;

    @Override
    public Double generate(Random random) {
        return value;
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private Double staticValue;

        public Builder value(Double staticValue) {
            this.staticValue = staticValue;
            return this;
        }


        @Override
        public StaticDoubleGenerator build() {
            return new StaticDoubleGenerator(this);
        }
    }

    private StaticDoubleGenerator(Builder builder) {
        this.value = builder.staticValue;
    }
}
