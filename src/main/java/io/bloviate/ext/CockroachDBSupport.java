package io.bloviate.ext;

import io.bloviate.db.Column;
import io.bloviate.gen.*;

import java.util.Random;

public class CockroachDBSupport extends AbstractDatabaseSupport {

    @Override
    public DataGenerator<?> buildArrayGenerator(Column column, Random random) {
        if ("_text".equalsIgnoreCase(column.getTypeName())) {
            return new StringArrayGenerator.Builder(random).build();
        } else if ("_int8".equalsIgnoreCase(column.getTypeName()) || "_int4".equalsIgnoreCase(column.getTypeName())) {
            return new IntegerArrayGenerator.Builder(random).build();
        } else {
            throw new UnsupportedOperationException("Data Type [" + column.getTypeName() + "] for ARRAY not supported");
        }
    }

    @Override
    public DataGenerator<?> buildOtherGenerator(Column column, Random random) {
        if ("uuid".equalsIgnoreCase(column.getTypeName())) {
            return new UUIDGenerator.Builder(random).build();
        } else if ("varbit".equalsIgnoreCase(column.getTypeName())) {
            return new BitStringGenerator.Builder(random).size(column.getMaxSize()).build();
        } else if ("inet".equalsIgnoreCase(column.getTypeName())) {
            return new InetGenerator.Builder(random).build();
        } else if ("interval".equalsIgnoreCase(column.getTypeName())) {
            return new IntervalGenerator.Builder(random).build();
        } else if ("jsonb".equalsIgnoreCase(column.getTypeName())) {
            return new JsonbGenerator.Builder(random).build();
        } else {
            throw new UnsupportedOperationException("Data Type [" + column.getTypeName() + "] for OTHER not supported");
        }
    }


}
