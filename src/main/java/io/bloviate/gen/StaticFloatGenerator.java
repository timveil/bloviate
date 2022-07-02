package io.bloviate.gen;

import java.util.Random;

public class StaticFloatGenerator extends AbstractDataGenerator<Float> {

    private final Float value;

    @Override
    public Float generate(Random random) {
        return value;
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private Float staticValue;

        public Builder value(Float staticValue) {
            this.staticValue = staticValue;
            return this;
        }


        @Override
        public StaticFloatGenerator build() {
            return new StaticFloatGenerator(this);
        }
    }

    private StaticFloatGenerator(Builder builder) {
        this.value = builder.staticValue;
    }
}
