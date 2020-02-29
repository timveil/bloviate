
```java
List<ColumnDefinition> definitions = new ArrayList<>();

definitions.add(new ColumnDefinition("col1", new SimpleStringGenerator.Builder().length(10).build()));
definitions.add(new ColumnDefinition("col2", new IntegerGenerator.Builder().start(1).end(100).build()));

new FlatFile.Builder("test").addAll(definitions).build().generate();
```