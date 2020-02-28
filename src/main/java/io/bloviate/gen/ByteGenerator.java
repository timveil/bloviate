package io.bloviate.gen;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;

public class ByteGenerator implements DataGenerator<Byte[]> {

    private final int size;

    @Override
    public Byte[] generate() {
        return ArrayUtils.toObject(RandomUtils.nextBytes(size));
    }

    public static class Builder {

        private int size;

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public ByteGenerator build() {
            return new ByteGenerator(this);
        }
    }

    private ByteGenerator(Builder builder) {
        this.size = builder.size;
    }
}
