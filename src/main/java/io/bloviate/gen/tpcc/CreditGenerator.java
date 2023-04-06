package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractDataGenerator;
import io.bloviate.util.SeededRandomUtils;

import java.util.Random;

public class CreditGenerator extends AbstractDataGenerator<String> {

    public static final String GC = "GC";
    public static final String BC = "BC";

    @Override
    public String generate(Random random) {

        String credit = GC;

        SeededRandomUtils randomUtils = new SeededRandomUtils(random);

        int randPct = randomUtils.nextInt(1, 100 + 1);

        if (randPct <= 10) {
            credit = BC;
        }

        return credit;
    }


}
