package io.bloviate.util;

import io.bloviate.db.*;

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

        DatabaseMetaData metaData = connection.getMetaData();

        return new Database(catalog, schema, getTables(metaData, catalog, schema));
    }

    private static List<Table> getTables(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {
        try (ResultSet tablesResultSet = metaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {

            List<Table> tables = new ArrayList<>();

            while (tablesResultSet.next()) {

                String tableName = tablesResultSet.getString("TABLE_NAME");

                List<Column> columns = getColumns(metaData, catalog, schema, tableName);
                PrimaryKey primaryKey = getPrimaryKey(metaData, catalog, schema, tableName);
                List<ForeignKey> foreignKeys = getForeignKeys(metaData, catalog, schema, tableName);

                tables.add(new Table(tableName, primaryKey, columns, foreignKeys));
            }

            return tables;
        }
    }

    private static List<ForeignKey> getForeignKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        try (ResultSet foreignKeysResultSet = metaData.getImportedKeys(catalog, schema, tableName)) {
            // key: pkTableName value: seq/colname
            Map<String, Map<Integer, KeyColumn>> map = new HashMap<>();

            while (foreignKeysResultSet.next()) {
                String primaryKeyTableName = foreignKeysResultSet.getString("PKTABLE_NAME");

                Map<Integer, KeyColumn> seqColMap;

                if (map.containsKey(primaryKeyTableName)) {
                    seqColMap = map.get(primaryKeyTableName);
                } else {
                    seqColMap = new HashMap<>();
                }

                int seq = foreignKeysResultSet.getInt("KEY_SEQ");
                String fkColumnName = foreignKeysResultSet.getString("FKCOLUMN_NAME");

                seqColMap.put(seq, new KeyColumn(seq, getColumn(metaData, catalog, schema, tableName, fkColumnName)));

                map.put(primaryKeyTableName, seqColMap);
            }

            List<ForeignKey> keys = new ArrayList<>();

            for (String primaryKeyTableName : map.keySet()) {

                Map<Integer, KeyColumn> seqColMap = map.get(primaryKeyTableName);
                List<KeyColumn> columns = new ArrayList<>(seqColMap.values());
                columns.sort(Comparator.comparing(KeyColumn::getSequence));

                keys.add(new ForeignKey(columns, getPrimaryKey(metaData, catalog, schema, primaryKeyTableName)));

            }


            return keys;
        }
    }

    private static PrimaryKey getPrimaryKey(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        try (ResultSet primaryKeyResultSet = metaData.getPrimaryKeys(catalog, schema, tableName)) {

            List<KeyColumn> keyColumns = new ArrayList<>();

            while (primaryKeyResultSet.next()) {

                String columnName = primaryKeyResultSet.getString("COLUMN_NAME");
                int sequence = primaryKeyResultSet.getInt("KEY_SEQ");

                keyColumns.add(new KeyColumn(sequence, getColumn(metaData, catalog, schema, tableName, columnName)));
            }

            keyColumns.sort(Comparator.comparing(KeyColumn::getSequence));

            return new PrimaryKey(tableName, keyColumns);

        }

    }

    private static Column getColumn(DatabaseMetaData metaData, String catalog, String schema, String tableName, String columnName) throws SQLException {

        try (ResultSet columnsResultSet = metaData.getColumns(catalog, schema, tableName, columnName)) {
            if (columnsResultSet.next()) {
                return mapColumn(columnsResultSet);
            }
        }

        throw new RuntimeException(String.format("can't find column in table [%s] with name [%s]", tableName, columnName));
    }

    private static List<Column> getColumns(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        try (ResultSet columnsResultSet = metaData.getColumns(catalog, schema, tableName, null)) {

            List<Column> columns = new ArrayList<>();

            while (columnsResultSet.next()) {
                columns.add(mapColumn(columnsResultSet));
            }

            columns.sort(Comparator.comparing(Column::ordinalPosition));

            return columns;

        }

    }

    private static Column mapColumn(ResultSet columnsResultSet) throws SQLException {
        String columnName = columnsResultSet.getString("COLUMN_NAME");
        String tableName = columnsResultSet.getString("TABLE_NAME");
        String catalog = columnsResultSet.getString("TABLE_CAT");
        String schema = columnsResultSet.getString("TABLE_SCHEM");

        int sqlType = columnsResultSet.getInt("DATA_TYPE");

        JDBCType jdbcType = JDBCType.valueOf(sqlType);

        // either number of characters or total precision, can be null.  in a decimal
        // this is the number of digits on both sides of the decimal point
        Integer maxSize = columnsResultSet.getObject("COLUMN_SIZE", Integer.class);

        // digits to right of decimal point (fractional digits), can be null
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

        String nullableString = columnsResultSet.getString("IS_NULLABLE");

        Boolean nullable = null;

        if ("YES".equalsIgnoreCase(nullableString)) {
            nullable = Boolean.TRUE;
        } else if ("NO".equalsIgnoreCase(nullableString)) {
            nullable = Boolean.FALSE;
        }

        String defaultValue = columnsResultSet.getString("COLUMN_DEF");

        int ordinalPosition = columnsResultSet.getInt("ORDINAL_POSITION");

        return new Column(columnName, tableName, schema, catalog, jdbcType, maxSize, maxDigits, typeName, autoIncrement, nullable, defaultValue, ordinalPosition);
    }

    // get the primary key column (column on other table) for the associated foreign key column (on this table)
    public static Column getAssociatedPrimaryKeyColumn(Database database, Table table, Column column) {

        // get this tables foreign keys
        List<ForeignKey> foreignKeys = table.getForeignKeys();

        if (foreignKeys != null && !foreignKeys.isEmpty()) {

            for (ForeignKey foreignKey : foreignKeys) {

                // get the columns on this table specified in the foreign key
                List<KeyColumn> keyColumns = foreignKey.getForeignKeyColumns();

                for (KeyColumn keyColumn : keyColumns) {
                    int keyColumnSequence = keyColumn.getSequence();

                    // this is a column on this table
                    Column foreignKeyColumn = keyColumn.getColumn();

                    if (foreignKeyColumn.equals(column)) {

                        // for the column on this table that is a foreign key, grab the associated column the another table where it is the primary key
                        PrimaryKey primaryKey = foreignKey.getPrimaryKey();

                        // for the primary key grab its full table data
                        Table primaryTable = database.getTable(primaryKey.getTableName());

                        for (KeyColumn primaryKeyColumn : primaryKey.getKeyColumns()) {

                            int primaryKeyColumnSequence = primaryKeyColumn.getSequence();

                            // let's make sure we are matching the right column in sequence
                            if (primaryKeyColumnSequence == keyColumnSequence) {

                                Column primaryKeyColumnColumn = primaryKeyColumn.getColumn();

                                Column possibleRoot = getAssociatedPrimaryKeyColumn(database, primaryTable, primaryKeyColumnColumn);

                                if (possibleRoot != null) {
                                    return possibleRoot;
                                }

                                return primaryKeyColumnColumn;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

}