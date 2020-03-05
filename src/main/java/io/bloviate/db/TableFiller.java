/*
 * Copyright 2020 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.db;

import io.bloviate.ColumnDefinition;
import io.bloviate.gen.*;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class TableFiller implements DatabaseFiller {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final Connection connection;
    private final String tableName;
    private final String catalog;
    private final String schemaPattern;
    private final String columnNamePattern;
    private final int rows;
    private final int batchSize;

    @Override
    public void fill() throws SQLException {

        DatabaseMetaData databaseMetaData = connection.getMetaData();

        //printTypeInfo(databaseMetaData);

        List<ColumnDefinition> definitions = getColumnDefinitions(databaseMetaData);

        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < definitions.size(); i++) {
            joiner.add("?");
        }
        String valuesString = joiner.toString();

        String sql = String.format("insert into %s values (%s)", tableName, valuesString);

        logger.debug(sql);

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            int rowCount = 0;
            for (int i = 0; i < rows; i++) {

                int colCount = 1;
                for (ColumnDefinition definition : definitions) {
                    if (definition.getDataGenerator() instanceof ByteGenerator) {
                        // todo: this feels sort of gross
                        ps.setBytes(colCount, ArrayUtils.toPrimitive(((ByteGenerator) definition.getDataGenerator()).generate()));
                    } else {
                        ps.setObject(colCount, definition.getDataGenerator().generate());
                    }
                    colCount++;
                }
                ps.addBatch();

                if (++rowCount % batchSize == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }

    }

    private void printTypeInfo(DatabaseMetaData databaseMetaData) {

        try (ResultSet columns = databaseMetaData.getTypeInfo()) {

            while (columns.next()) {

                String typeName = columns.getString("TYPE_NAME");
                JDBCType jdbcType = JDBCType.valueOf(columns.getInt("DATA_TYPE"));
                int precision = columns.getInt("PRECISION");

                logger.debug("name [{}], jdbcType [{}], precision [{}]", typeName, jdbcType.getName(), precision);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private List<ColumnDefinition> getColumnDefinitions(DatabaseMetaData databaseMetaData) throws SQLException {

        List<ColumnDefinition> definitions = new ArrayList<>();

        try (ResultSet columns = databaseMetaData.getColumns(catalog, schemaPattern, tableName, columnNamePattern)) {

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                int sqlType = columns.getInt("DATA_TYPE");

                // either number of characters or total precision, can be null
                Integer maxSize = columns.getObject("COLUMN_SIZE", Integer.class);

                // digits to right of decimal point, can be null
                Integer maxDigits = columns.getObject("DECIMAL_DIGITS", Integer.class);

                String typeName = columns.getString("TYPE_NAME");

                JDBCType jdbcType = JDBCType.valueOf(sqlType);

                logger.debug("tableName [{}], columnName [{}], jdbcType [{}], typeName [{}], maxSize[{}], maxDigits[{}]", tableName, columnName, jdbcType.getName(), typeName, maxSize, maxDigits);

                definitions.add(new ColumnDefinition(columnName, getDataGenerator(jdbcType, typeName, maxSize, maxDigits)));

            }

        }
        return definitions;
    }

    private DataGenerator getDataGenerator(JDBCType jdbcType, String typeName, Integer maxSize, Integer maxDigits) {
        DataGenerator generator;

        switch (jdbcType) {

            case TINYINT:
                generator = new ShortGenerator.Builder().start(0).end(255).build();
                break;
            case SMALLINT:
                generator = new ShortGenerator.Builder().build();
                break;
            case INTEGER:
                generator = new IntegerGenerator.Builder().build();
                break;
            case BIGINT:
                generator = new LongGenerator.Builder().build();
                break;
            case FLOAT:
            case REAL:
                generator = new FloatGenerator.Builder().build();
                break;
            case DOUBLE:
                generator = new DoubleGenerator.Builder().build();
                break;
            case NUMERIC:
            case DECIMAL:
                generator = new BigDecimalGenerator.Builder().precision(maxSize).digits(maxDigits).build();
                break;
            case CHAR:
            case VARCHAR:
            case LONGVARCHAR:
            case NCHAR:
            case NVARCHAR:
            case LONGNVARCHAR:
                generator = new SimpleStringGenerator.Builder().size(maxSize).build();
                break;
            case DATE:
                generator = new SqlDateGenerator.Builder().build();
                break;
            case TIME:
                generator = new SqlTimeGenerator.Builder().build();
                break;
            case TIMESTAMP:
                generator = new SqlTimestampGenerator.Builder().build();
                break;
            case BINARY:
            case VARBINARY:
            case LONGVARBINARY:
                generator = new ByteGenerator.Builder().size(maxSize).build();
                break;
            case BLOB:
                generator = new SqlBlobGenerator.Builder().build();
                break;
            case CLOB:
            case NCLOB:
                generator = new SqlClobGenerator.Builder().build();
                break;
            case STRUCT:
                generator = new SqlStructGenerator.Builder().build();
                break;
            case ARRAY:
                if ("_text".equalsIgnoreCase(typeName)) {
                    generator = new SqlArrayGenerator.Builder().type(SqlArrayType.STRING).build();
                } else if ("_int8".equalsIgnoreCase(typeName)) {
                    generator = new SqlArrayGenerator.Builder().type(SqlArrayType.INT).build();
                } else {
                    throw new UnsupportedOperationException("Data Type [" + typeName + "] for ARRAY not supported");
                }
                break;
            case BIT:
                generator = new BitGenerator.Builder().length(maxSize).build();
                break;
            case BOOLEAN:
                generator = new BooleanGenerator.Builder().build();
                break;
            case OTHER:
                if ("uuid".equalsIgnoreCase(typeName)) {
                    generator = new UUIDGenerator.Builder().build();
                } else if ("varbit".equalsIgnoreCase(typeName)) {
                    generator = new BitGenerator.Builder().length(maxSize).build();
                } else if ("inet".equalsIgnoreCase(typeName)) {
                    generator = new InetGenerator.Builder().build();
                } else {
                    throw new UnsupportedOperationException("Data Type [" + typeName + "] for OTHER not supported");
                }
                break;
            case TIME_WITH_TIMEZONE:
            case TIMESTAMP_WITH_TIMEZONE:
            case JAVA_OBJECT:
            case DISTINCT:
            case REF:
            case DATALINK:
            case ROWID:
            case SQLXML:
            case REF_CURSOR:
            case NULL:
                throw new UnsupportedOperationException("JDBCType [" + jdbcType + "] not supported");
            default:
                throw new IllegalStateException("Unexpected value: " + jdbcType);
        }

        return generator;
    }

    public static class Builder {

        private final Connection connection;
        private final String tableName;

        private String catalog = null;
        private String schemaPattern = "public";
        private String columnNamePattern = null;
        private int rows = 1000;
        private int batchSize = 128;

        public Builder(Connection connection, String tableName) {
            this.connection = connection;
            this.tableName = tableName;
        }

        public Builder catalog(String catalog) {
            this.catalog = catalog;
            return this;
        }

        public Builder schemaPattern(String schemaPattern) {
            this.schemaPattern = schemaPattern;
            return this;
        }

        public Builder columnNamePattern(String columnNamePattern) {
            this.columnNamePattern = columnNamePattern;
            return this;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public TableFiller build() {
            return new TableFiller(this);
        }
    }

    private TableFiller(Builder builder) {
        this.connection = builder.connection;
        this.tableName = builder.tableName;
        this.catalog = builder.catalog;
        this.schemaPattern = builder.schemaPattern;
        this.columnNamePattern = builder.columnNamePattern;
        this.rows = builder.rows;
        this.batchSize = builder.batchSize;
    }
}
