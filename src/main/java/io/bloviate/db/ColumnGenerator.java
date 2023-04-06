package io.bloviate.db;

import io.bloviate.db.metadata.Column;
import io.bloviate.gen.DataGenerator;

import java.util.Random;

public record ColumnGenerator(Column column, DataGenerator dataGenerator, long seed, Random random) {
}
