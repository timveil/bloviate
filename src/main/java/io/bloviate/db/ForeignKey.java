package io.bloviate.db;

import java.util.List;

public record ForeignKey(List<KeyColumn> foreignKeyColumns, PrimaryKey primaryKey) {}
