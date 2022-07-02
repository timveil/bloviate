package io.bloviate.gen;

import java.util.Random;

public class StaticIntegerGenerator extends AbstractDataGenerator<Integer> {

    private final Integer value;

    @Override
    public Integer generate(Random random) {
        return value;
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private Integer staticValue;

        public Builder value(Integer staticValue) {
            this.staticValue = staticValue;
            return this;
        }


        @Override
        public StaticIntegerGenerator build() {
            return new StaticIntegerGenerator(this);
        }
    }

    private StaticIntegerGenerator(Builder builder) {
        this.value = builder.staticValue;
    }
}
