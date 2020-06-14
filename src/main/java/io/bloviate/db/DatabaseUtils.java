package io.bloviate.db;

import io.bloviate.gen.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class DatabaseUtils {


    public static Database getMetadata(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return getMetadata(connection);
        }
    }

    public static Database getMetadata(Connection connection) throws SQLException {


        String catalog = connection.getCatalog();
        String schema = connection.getSchema();

        Map<String,  Map<String, Column>> tableColumnMap = new HashMap<>();
        Map<String,  List<PrimaryKey>> tableKeyMap = new HashMap<>();

        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tablesResultSet = metaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (tablesResultSet.next()) {

                String tableName = tablesResultSet.getString("TABLE_NAME");

                Map<String, Column> columnMap = new HashMap<>();

                try (ResultSet columnsResultSet = metaData.getColumns(catalog, schema, tableName, null)) {

                    while (columnsResultSet.next()) {
                        String columnName = columnsResultSet.getString("COLUMN_NAME");
                        int sqlType = columnsResultSet.getInt("DATA_TYPE");

                        JDBCType jdbcType = JDBCType.valueOf(sqlType);

                        // either number of characters or total precision, can be null
                        Integer maxSize = columnsResultSet.getObject("COLUMN_SIZE", Integer.class);

                        // digits to right of decimal point, can be null
                        Integer maxDigits = null;

                        if (jdbcType.equals(JDBCType.NUMERIC) || jdbcType.equals(JDBCType.DECIMAL)) {
                            // only care if its one of these types
                            maxDigits = columnsResultSet.getObject("DECIMAL_DIGITS", Integer.class);
                        }

                        String typeName = columnsResultSet.getString("TYPE_NAME");

                        String autoIncrementString = columnsResultSet.getString("IS_AUTOINCREMENT");

                        Boolean autoIncrement = null;

                        if ("YES".equalsIgnoreCase(autoIncrementString)) {
                            autoIncrement = Boolean.TRUE;
                        } else if ("NO".equalsIgnoreCase(autoIncrementString)) {
                            autoIncrement = Boolean.FALSE;
                        }

                        String defaultValue = columnsResultSet.getString("COLUMN_DEF");

                        int ordinalPosition = columnsResultSet.getInt("ORDINAL_POSITION");

                        columnMap.put(columnName, new Column(columnName, jdbcType, maxSize, maxDigits, typeName, autoIncrement, defaultValue, ordinalPosition, getDataGenerator(jdbcType, typeName, maxSize, maxDigits)));

                    }

                }

                List<PrimaryKey> primaryKeys = new ArrayList<>();

                try (ResultSet primaryKeyResultSet = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                    while (primaryKeyResultSet.next()) {
                        String columnName = primaryKeyResultSet.getString("COLUMN_NAME");

                       primaryKeys.add(new PrimaryKey(columnMap.get(columnName)));
                    }
                }

                tableKeyMap.put(tableName, primaryKeys);
                tableColumnMap.put(tableName, columnMap);
            }
        }

        Map<String,  List<ForeignKey>> foreignKeyMap = new HashMap<>();

        for (String tableName : tableColumnMap.keySet()) {

            List<ForeignKey> keys = new ArrayList<>();

            try (ResultSet foreignKeysResultSet = metaData.getImportedKeys(catalog, schema, tableName)) {
                while (foreignKeysResultSet.next()) {

                    Map<String, Column> thisTableColumnMap = tableColumnMap.get(tableName);

                    // FK is the column on this table
                    String columnName = foreignKeysResultSet.getString("FKCOLUMN_NAME");

                    Column thisColumn = thisTableColumnMap.get(columnName);

                    // PK is the column on another table referenced by FK above
                    String pkTableName = foreignKeysResultSet.getString("PKTABLE_NAME");
                    String pkColumnName = foreignKeysResultSet.getString("PKCOLUMN_NAME");

                    Map<String, Column> thatTableColumnMap = tableColumnMap.get(pkTableName);

                    Column thatColumn = thatTableColumnMap.get(pkColumnName);

                    keys.add(new ForeignKey(thisColumn, pkTableName, thatColumn));

                }
            }

            foreignKeyMap.put(tableName, keys);
        }


        List<Table> tables = new ArrayList<>();
        for (String tableName : tableColumnMap.keySet()) {

            Map<String, Column> stringColumnMap = tableColumnMap.get(tableName);
            List<Column> columns = new ArrayList<>(stringColumnMap.values());
            columns.sort(Comparator.comparing(Column::getOrdinalPosition));

            List<PrimaryKey> primaryKeys = tableKeyMap.get(tableName);
            List<ForeignKey> foreignKeys = foreignKeyMap.get(tableName);

            tables.add(new Table(tableName, columns, primaryKeys, foreignKeys));
        }

        return new Database(catalog, schema, tables);

    }


    private static DataGenerator<?> getDataGenerator(JDBCType jdbcType, String typeName, Integer maxSize, Integer maxDigits) {
        DataGenerator<?> generator;

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
            case TIME_WITH_TIMEZONE:
                generator = new SqlTimeGenerator.Builder().build();
                break;
            case TIMESTAMP:
            case TIMESTAMP_WITH_TIMEZONE:
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
                    generator = new StringArrayGenerator.Builder().build();
                } else if ("_int8".equalsIgnoreCase(typeName)) {
                    generator = new IntegerArrayGenerator.Builder().build();
                } else {
                    throw new UnsupportedOperationException("Data Type [" + typeName + "] for ARRAY not supported");
                }
                break;
            case BIT:
                generator = new BitGenerator.Builder().size(maxSize).build();
                break;
            case BOOLEAN:
                generator = new BooleanGenerator.Builder().build();
                break;
            case OTHER:
                if ("uuid".equalsIgnoreCase(typeName)) {
                    generator = new UUIDGenerator.Builder().build();
                } else if ("varbit".equalsIgnoreCase(typeName)) {
                    generator = new BitGenerator.Builder().size(maxSize).build();
                } else if ("inet".equalsIgnoreCase(typeName)) {
                    generator = new InetGenerator.Builder().build();
                } else if ("interval".equalsIgnoreCase(typeName)) {
                    generator = new IntervalGenerator.Builder().build();
                } else if ("jsonb".equalsIgnoreCase(typeName)) {
                    generator = new JsonbGenerator.Builder().build();
                } else {
                    throw new UnsupportedOperationException("Data Type [" + typeName + "] for OTHER not supported");
                }
                break;
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
}