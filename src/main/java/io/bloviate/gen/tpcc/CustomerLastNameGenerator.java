package io.bloviate.gen.tpcc;

import io.bloviate.gen.AbstractDataGenerator;
import io.bloviate.util.SeededRandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

public class CustomerLastNameGenerator extends AbstractDataGenerator<String> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

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


    @Override
    public String generate(Random random) {
        SeededRandomUtils randomUtils = new SeededRandomUtils(random);
        String randomNumber = StringUtils.leftPad(String.valueOf(randomUtils.nextInt(0, 1000)), 3, '0');

        String lastName = syllables[Integer.parseInt(randomNumber.substring(0, 1))]
                   + syllables[Integer.parseInt(randomNumber.substring(1, 2))]
                   + syllables[Integer.parseInt(randomNumber.substring(2, 3))];

        logger.trace("last name = [{}] for number = [{}]", lastName, randomNumber);

        return lastName;
    }

    @Override
    public String get(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }
}
