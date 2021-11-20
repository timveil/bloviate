package io.bloviate.gen;

import java.util.Random;

public abstract class AbstractBuilder {
    protected final Random random;

    public AbstractBuilder(Random random) {
        this.random = random;
    }
}
