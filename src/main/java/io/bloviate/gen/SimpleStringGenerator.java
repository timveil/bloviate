package io.bloviate.gen;

import org.apache.commons.lang3.RandomStringUtils;

public class SimpleStringGenerator implements DataGenerator<String> {

    private final int length;
    private final boolean letters;
    private final boolean numbers;

    @Override
    public String generate() {
        return RandomStringUtils.random(length, letters, numbers);
    }

    public static class Builder {

        private int length = 10;

        private boolean letters = true;

        private boolean numbers = false;

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder letters(boolean letters) {
            this.letters = letters;
            return this;
        }

        public Builder numbers(boolean numbers) {
            this.numbers = letters;
            return this;
        }

        public SimpleStringGenerator build() {
            return new SimpleStringGenerator(this);
        }
    }

    private SimpleStringGenerator(Builder builder) {
        this.length = builder.length;
        this.letters = builder.letters;
        this.numbers = builder.numbers;
    }
}
