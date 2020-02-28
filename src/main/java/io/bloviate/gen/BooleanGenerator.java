package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class BooleanGenerator implements DataGenerator<Boolean> {

    @Override
    public Boolean generate() {
        return RandomUtils.nextBoolean();
    }

    public static class Builder {
        public BooleanGenerator build() {
            return new BooleanGenerator(this);
        }
    }

    private BooleanGenerator(Builder builder) {

    }
}
