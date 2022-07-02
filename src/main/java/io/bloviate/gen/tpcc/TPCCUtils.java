package io.bloviate.gen.tpcc;

import java.util.Random;

public class TPCCUtils {

    private static final String[] syllables = new String[]{
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

    private static int randomNumber(int min, int max, Random r) {
        return (int) (r.nextDouble() * (max - min + 1) + min);
    }

    private static int nonUniformRandom(int A, int C, int min, int max, Random r) {
        return (((randomNumber(0, A, r) | randomNumber(min, max, r)) + C) % (max - min + 1)) + min;
    }

    private static String getLastName(int num) {
        return syllables[num / 100] +
               syllables[(num / 10) % 10] +
               syllables[num % 10];
    }

    public static String getNonUniformRandomLastName(Random r, int C) {
        return getLastName(nonUniformRandom(255, C, 0, 999, r));
    }


}
