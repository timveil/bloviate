package io.bloviate.db;

import io.bloviate.gen.DataGenerator;

import java.sql.JDBCType;
import java.util.Objects;

public class Column {

    private final String name;
    private final String tableName;
    private final String schema;
    private final String catalog;
    private final JDBCType jdbcType;
    private final Integer maxSize;
    private final Integer maxDigits;
    private final String typeName;
    private final Boolean autoIncrement;
    private final Boolean nullable;
    private final String defaultValue;
    private final Integer ordinalPosition;
    private final DataGenerator<?> dataGenerator;


    public Column(String name, String tableName, String schema, String catalog, JDBCType jdbcType, Integer maxSize, Integer maxDigits, String typeName, Boolean autoIncrement, Boolean nullable, String defaultValue, Integer ordinalPosition, DataGenerator<?> dataGenerator) {
        this.name = name;
        this.tableName = tableName;
        this.schema = schema;
        this.catalog = catalog;
        this.jdbcType = jdbcType;
        this.maxSize = maxSize;
        this.maxDigits = maxDigits;
        this.typeName = typeName;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.ordinalPosition = ordinalPosition;
        this.dataGenerator = dataGenerator;
    }

    public String getName() {
        return name;
    }

    public String getTableName() {
        return tableName;
    }

    public JDBCType getJdbcType() {
        return jdbcType;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public Integer getMaxDigits() {
        return maxDigits;
    }

    public String getTypeName() {
        return typeName;
    }

    public Boolean getAutoIncrement() {
        return autoIncrement;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Integer getOrdinalPosition() {
        return ordinalPosition;
    }

    public DataGenerator<?> getDataGenerator() {
        return dataGenerator;
    }

    public String getSchema() {
        return schema;
    }

    public String getCatalog() {
        return catalog;
    }

    public Boolean getNullable() {
        return nullable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Column column = (Column) o;
        return Objects.equals(name, column.name) &&
                Objects.equals(tableName, column.tableName) &&
                Objects.equals(schema, column.schema) &&
                Objects.equals(catalog, column.catalog) &&
                jdbcType == column.jdbcType &&
                Objects.equals(maxSize, column.maxSize) &&
                Objects.equals(maxDigits, column.maxDigits) &&
                Objects.equals(typeName, column.typeName) &&
                Objects.equals(autoIncrement, column.autoIncrement) &&
                Objects.equals(nullable, column.nullable) &&
                Objects.equals(defaultValue, column.defaultValue) &&
                Objects.equals(ordinalPosition, column.ordinalPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tableName, schema, catalog, jdbcType, maxSize, maxDigits, typeName, autoIncrement, nullable, defaultValue, ordinalPosition);
    }

    @Override
    public String toString() {
        return "Column{" +
                "name='" + name + '\'' +
                ", tableName='" + tableName + '\'' +
                ", schema='" + schema + '\'' +
                ", catalog='" + catalog + '\'' +
                ", jdbcType=" + jdbcType +
                ", maxSize=" + maxSize +
                ", maxDigits=" + maxDigits +
                ", typeName='" + typeName + '\'' +
                ", autoIncrement=" + autoIncrement +
                ", nullable=" + nullable +
                ", defaultValue='" + defaultValue + '\'' +
                ", ordinalPosition=" + ordinalPosition +
                '}';
    }
}
