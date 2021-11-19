package io.bloviate.gen;

import java.util.Random;

public abstract class AbstractBuilder implements Builder {
    protected final Random random;

    public AbstractBuilder(Random random) {
        this.random = random;
    }
}
