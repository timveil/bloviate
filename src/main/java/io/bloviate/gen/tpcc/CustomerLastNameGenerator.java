package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class CustomerLastNameGenerator extends AbstractDataGenerator<String> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final int C_LAST_LOAD_C = 157; // in range [0, 255]
    private static final int C_LAST_RUN_C = 223; // in range [0, 255]



    @Override
    public String generate(Random random) {
        return TPCCUtils.getNonUniformRandomLastName(random, C_LAST_LOAD_C);
    }

}
