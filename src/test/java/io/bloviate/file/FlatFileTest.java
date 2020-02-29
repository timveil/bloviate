package io.bloviate.file;

import io.bloviate.ColumnDefinition;
import io.bloviate.gen.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class FlatFileTest {

    @Test
    void generate() {

        List<ColumnDefinition> definitions = new ArrayList<>();

        definitions.add(new ColumnDefinition("boolean_col", new BooleanGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("byte_col", new ByteGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("date_col", new DateGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("double_col", new DoubleGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("float_col", new FloatGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("integer_col", new IntegerGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("long_col", new LongGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("short_col", new ShortGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("string_col", new SimpleStringGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("sql_date_col", new SqlDateGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("sql_time_col", new SqlTimeGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("sql_timestamp_col", new SqlTimestampGenerator.Builder().build()));
        definitions.add(new ColumnDefinition("sql_uuid_col", new UUIDGenerator.Builder().build()));

        new FlatFile.Builder("test").addAll(definitions).build().generate();
    }
}