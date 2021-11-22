package io.bloviate.db;

import java.sql.JDBCType;

public record Column(String name, String tableName, String schema, String catalog, JDBCType jdbcType, Integer maxSize, Integer maxDigits, String typeName, Boolean autoIncrement, Boolean nullable, String defaultValue, Integer ordinalPosition) {}
