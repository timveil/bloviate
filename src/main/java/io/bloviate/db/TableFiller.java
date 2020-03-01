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

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TableFiller implements DatabaseFiller {

    private final Connection connection;
    private final String tableName;
    private final String catalog;
    private final String schemaPattern;
    private final String columnNamePattern;

    @Override
    public void fill() throws SQLException {

        DatabaseMetaData databaseMetaData = connection.getMetaData();

        List<ColumnDefinition> definitions = getColumnDefinitions(databaseMetaData);

    }

    private List<ColumnDefinition> getColumnDefinitions(DatabaseMetaData databaseMetaData) throws SQLException {
        List<ColumnDefinition> definitions = new ArrayList<>();

        try (ResultSet columns = databaseMetaData.getColumns(catalog, schemaPattern, tableName, columnNamePattern)) {

            while (columns.next()) {
                String name = columns.getString("COLUMN_NAME");
                int sqlType = columns.getInt("DATA_TYPE");
                int size = columns.getInt("COLUMN_SIZE");
                int decimalDigits = columns.getInt("DECIMAL_DIGITS");
                int radix = columns.getInt("NUM_PREC_RADIX");

                JDBCType jdbcType = JDBCType.valueOf(sqlType);

                definitions.add(new ColumnDefinition(name, getDataGenerator(jdbcType, size)));

            }

        }
        return definitions;
    }

    private DataGenerator getDataGenerator(JDBCType jdbcType, int size) {
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
                generator = new BigDecimalGenerator.Builder().build();
                break;
            case CHAR:
            case VARCHAR:
            case LONGVARCHAR:
            case NCHAR:
            case NVARCHAR:
            case LONGNVARCHAR:
                generator = new SimpleStringGenerator.Builder().length(size).build();
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
                generator = new ByteGenerator.Builder().size(size).build();
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
                generator = new SqlArrayGenerator.Builder().build();
                break;
            case BIT:
            case BOOLEAN:
                generator = new BooleanGenerator.Builder().build();
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
            case OTHER:
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
    }
}
