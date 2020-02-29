
```java
List<ColumnDefinition> definitions = new ArrayList<>();

definitions.add(new ColumnDefinition("boolean_col", new BooleanGenerator.Builder().build()));
definitions.add(new ColumnDefinition("byte_col", new ByteGenerator.Builder(10).build()));
definitions.add(new ColumnDefinition("date_col", new DateGenerator.Builder().build()));
definitions.add(new ColumnDefinition("double_col", new DoubleGenerator.Builder().build()));
definitions.add(new ColumnDefinition("float_col", new FloatGenerator.Builder().build()));
definitions.add(new ColumnDefinition("integer_col", new IntegerGenerator.Builder().start(1).end(100).build()));
definitions.add(new ColumnDefinition("long_col", new LongGenerator.Builder().build()));
definitions.add(new ColumnDefinition("short_col", new ShortGenerator.Builder().build()));
definitions.add(new ColumnDefinition("string_col", new SimpleStringGenerator.Builder().length(10).build()));
definitions.add(new ColumnDefinition("sql_date_col", new SqlDateGenerator.Builder().build()));
definitions.add(new ColumnDefinition("sql_time_col", new SqlTimeGenerator.Builder().build()));
definitions.add(new ColumnDefinition("sql_timestamp_col", new SqlTimestampGenerator.Builder().build()));

new FlatFile.Builder("test").addAll(definitions).build().generate();
```