package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractDataGenerator;

import java.util.Random;

public class CustomerLastNameGenerator extends AbstractDataGenerator<String> {

    private static final String[] SYLLABLES = new String[]{
            "BAR", // 0
            "OUGHT", // 1
            "ABLE", // 2
            "PRI", // 3
            "PRES", // 4
            "ESE", // 5
            "ANTI", // 6
            "CALLY", // 7
            "ATION", // 8
            "EING" // 9
    };

    private static final int C_FOR_LOAD = 157; // in range [0, 255]
    private static final int C_FOR_RUN = 223; // in range [0, 255]

    @Override
    public String generate(Random random) {

        int num = TPCCUtils.nonUniformRandom(255, C_FOR_LOAD, 0, 999, random);

        return getLastName(num);

    }

    private String getLastName(int num) {
        return SYLLABLES[num / 100] +
               SYLLABLES[(num / 10) % 10] +
               SYLLABLES[num % 10];
    }

}
