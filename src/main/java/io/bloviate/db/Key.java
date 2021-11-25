package io.bloviate.db;

public record Key(String primaryTableName, String primaryColumnName, String foreignTableName, String foreignColumnName, int sequence, String name) {
}
