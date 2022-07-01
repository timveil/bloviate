package io.bloviate.db;

import io.bloviate.gen.DataGenerator;

public record ColumnConfiguration(String columnName, DataGenerator dataGenerator) {
}
