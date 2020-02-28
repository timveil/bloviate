package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

public class CharacterGenerator implements DataGenerator<Character> {

    @Override
    public Character generate() {
        return (char) (RandomUtils.nextInt(0, 26) + 'a');
    }

    public static class Builder {
        public CharacterGenerator build() {
            return new CharacterGenerator(this);
        }
    }

    private CharacterGenerator(Builder builder) {

    }
}
