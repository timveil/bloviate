package io.bloviate.db;

import java.util.List;


public record PrimaryKey(String tableName, List<KeyColumn> keyColumns) {}
