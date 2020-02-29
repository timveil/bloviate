package io.bloviate.gen;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;

public class ByteGenerator implements DataGenerator<Byte[]> {

    private final int size;

    @Override
    public Byte[] generate() {
        return ArrayUtils.toObject(RandomUtils.nextBytes(size));
    }

    @Override
    public String generateAsString() {
        return null;
    }


    public static class Builder {

        private final int size;

        public Builder(int size) {
            this.size = size;
        }

        public ByteGenerator build() {
            return new ByteGenerator(this);
        }
    }

    private ByteGenerator(Builder builder) {
        this.size = builder.size;
    }
}
