package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class BooleanGenerator implements DataGenerator<Boolean> {

    @Override
    public Boolean generate() {
        return RandomUtils.nextBoolean();
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {
        public BooleanGenerator build() {
            return new BooleanGenerator(this);
        }
    }

    private BooleanGenerator(Builder builder) {

    }
}
