package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractDataGenerator;
import io.bloviate.util.SeededRandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;

public class ZipCodeGenerator extends AbstractDataGenerator<String> {
    @Override
    public String generate(Random random) {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);

        return StringUtils.leftPad(Integer.toString(randomUtils.nextInt(0, 1000)), 4, '0') + "11111";
    }
}
