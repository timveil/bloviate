package io.bloviate.gen;

import io.bloviate.db.metadata.Column;
import io.bloviate.db.metadata.KeyColumn;
import io.bloviate.db.metadata.PrimaryKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SimplePrimaryKeyGenerator implements PrimaryKeyGenerator {

    private final Map<KeyColumn, DataGenerator<?>> dataGenerators;


    @Override
    public Map<Column, ColumnValue<?>> generate(PrimaryKey primaryKey, Random random) {

        Map<Column, ColumnValue<?>> valueMap = new HashMap<>();

        for (KeyColumn keyColumn : primaryKey.keyColumns()) {
            Column column = keyColumn.column();
            valueMap.put(column, new ColumnValue<>(column, dataGenerators.get(keyColumn).generate(random)));
        }


        return valueMap;
    }

    public static class Builder implements io.bloviate.gen.PrimaryKeyBuilder {

        private final Map<KeyColumn, DataGenerator<?>> dataGenerators;

        public Builder(Map<KeyColumn, DataGenerator<?>> dataGenerators) {
            this.dataGenerators = dataGenerators;
        }


        @Override
        public SimplePrimaryKeyGenerator build() {
            return new SimplePrimaryKeyGenerator(this);
        }
    }

    private SimplePrimaryKeyGenerator(Builder builder) {
        this.dataGenerators = builder.dataGenerators;
    }
}

