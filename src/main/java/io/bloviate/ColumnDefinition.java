package io.bloviate;

import io.bloviate.gen.DataGenerator;

public class ColumnDefinition {

    private final String header;
    private final DataGenerator dataGenerator;

    public ColumnDefinition(String header, DataGenerator dataGenerator) {
        this.header = header;
        this.dataGenerator = dataGenerator;
    }

    public String getHeader() {
        return header;
    }

    public DataGenerator getDataGenerator() {
        return dataGenerator;
    }
}
