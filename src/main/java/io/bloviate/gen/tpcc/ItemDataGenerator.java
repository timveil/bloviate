package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractDataGenerator;
import io.bloviate.util.SeededRandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;

public class ItemDataGenerator extends AbstractDataGenerator<String> {


    public static final String ORIGINAL = "ORIGINAL";

    @Override
    public String generate(Random random) {

        String data;

        SeededRandomUtils randomUtils = new SeededRandomUtils(random);

        int randPct = randomUtils.nextInt(1, 100 + 1);
        int dataLength = randomUtils.nextInt(26, 50 + 1);

        data = randomUtils.randomAlphabetic(dataLength);

        if (randPct <= 10) {
            int randomStart = randomUtils.nextInt(0, data.length() - ORIGINAL.length());
            String replace = StringUtils.substring(data, randomStart, randomStart + ORIGINAL.length());
            data = StringUtils.replace(data, replace, ORIGINAL);
        }

        return data;
    }


}
