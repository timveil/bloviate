package io.bloviate.db;

import io.bloviate.gen.DataGenerator;

import java.sql.JDBCType;
import java.util.Objects;

public class Column {

    private final String name;
    private final JDBCType jdbcType;
    private final Integer maxSize;
    private final Integer maxDigits;
    private final String typeName;
    private final Boolean autoIncrement;
    private final String defaultValue;
    private final Integer ordinalPosition;
    private final DataGenerator<?> dataGenerator;

    private boolean primaryKey;
    private Column foreignKey;

    public Column(String name, JDBCType jdbcType, Integer maxSize, Integer maxDigits, String typeName, Boolean autoIncrement, String defaultValue, Integer ordinalPosition, DataGenerator<?> dataGenerator) {
        this.name = name;
        this.jdbcType = jdbcType;
        this.maxSize = maxSize;
        this.maxDigits = maxDigits;
        this.typeName = typeName;
        this.autoIncrement = autoIncrement;
        this.defaultValue = defaultValue;
        this.ordinalPosition = ordinalPosition;
        this.dataGenerator = dataGenerator;
    }

    public String getName() {
        return name;
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

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Column getForeignKey() {
        return foreignKey;
    }

    public void setForeignKey(Column foreignKey) {
        this.foreignKey = foreignKey;
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
                jdbcType == column.jdbcType &&
                Objects.equals(maxSize, column.maxSize) &&
                Objects.equals(maxDigits, column.maxDigits) &&
                Objects.equals(typeName, column.typeName) &&
                Objects.equals(autoIncrement, column.autoIncrement) &&
                Objects.equals(defaultValue, column.defaultValue) &&
                Objects.equals(ordinalPosition, column.ordinalPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, jdbcType, maxSize, maxDigits, typeName, autoIncrement, defaultValue, ordinalPosition);
    }

    @Override
    public String toString() {
        return "Column{" +
                "name='" + name + '\'' +
                ", jdbcType=" + jdbcType +
                ", maxSize=" + maxSize +
                ", maxDigits=" + maxDigits +
                ", typeName='" + typeName + '\'' +
                ", autoIncrement=" + autoIncrement +
                ", defaultValue='" + defaultValue + '\'' +
                ", ordinalPosition=" + ordinalPosition +
                ", dataGenerator=" + dataGenerator +
                ", primaryKey=" + primaryKey +
                ", foreignKey=" + foreignKey +
                '}';
    }
}
