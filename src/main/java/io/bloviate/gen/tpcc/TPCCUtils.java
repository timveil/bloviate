package io.bloviate.gen.tpcc;

import io.bloviate.util.SeededRandomUtils;

import java.util.Random;

public class TPCCUtils {

    public static int nonUniformRandom(int A, int C, int min, int max, Random r) {
        SeededRandomUtils randomUtils = new SeededRandomUtils(r);
        return (((randomUtils.nextInt(0, A + 1) | randomUtils.nextInt(min, max + 1)) + C) % (max - min + 1)) + min;
    }

}
