package io.bloviate;

import io.bloviate.gen.DataGenerator;

public class ColumnDefinition {

    private final String name;
    private final DataGenerator dataGenerator;

    public ColumnDefinition(String name, DataGenerator dataGenerator) {
        this.name = name;
        this.dataGenerator = dataGenerator;
    }

    public String getName() {
        return name;
    }

    public DataGenerator getDataGenerator() {
        return dataGenerator;
    }
}
