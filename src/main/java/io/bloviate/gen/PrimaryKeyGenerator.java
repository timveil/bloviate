package io.bloviate.gen;

import io.bloviate.db.metadata.Column;
import io.bloviate.db.metadata.PrimaryKey;

import java.util.Map;
import java.util.Random;

public interface PrimaryKeyGenerator {

    Map<Column, ColumnValue<?>> generate(PrimaryKey primaryKey, Random random);
}
