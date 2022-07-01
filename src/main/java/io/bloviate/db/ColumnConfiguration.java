package io.bloviate.db;

import io.bloviate.gen.DataGenerator;

public record ColumnConfiguration<T>(String columnName, DataGenerator<T> dataGenerator) {
}
