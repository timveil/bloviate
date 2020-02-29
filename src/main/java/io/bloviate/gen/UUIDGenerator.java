package io.bloviate.gen;

import java.util.UUID;

public class UUIDGenerator implements DataGenerator<UUID> {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {
        public UUIDGenerator build() {
            return new UUIDGenerator(this);
        }
    }

    private UUIDGenerator(Builder builder) {

    }
}
