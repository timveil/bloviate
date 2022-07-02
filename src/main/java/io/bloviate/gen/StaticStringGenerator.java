package io.bloviate.gen;

import java.util.Random;

public class StaticStringGenerator extends AbstractDataGenerator<String> {

    private final String value;

    @Override
    public String generate(Random random) {
        return value;
    }

    public static class Builder implements io.bloviate.gen.Builder {

        private String staticValue;

        public Builder value(String staticValue) {
            this.staticValue = staticValue;
            return this;
        }


        @Override
        public StaticStringGenerator build() {
            return new StaticStringGenerator(this);
        }
    }

    private StaticStringGenerator(Builder builder) {
        this.value = builder.staticValue;
    }
}
