/*
 * Copyright (c) 2021 Tim Veil
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

package io.bloviate.util;

import io.bloviate.db.metadata.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class DatabaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtils.class);

    public static Database getMetadata(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return getMetadata(connection);
        }
    }

    public static Database getMetadata(Connection connection) throws SQLException {
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();

        DatabaseMetaData metaData = connection.getMetaData();

        return new Database(metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion(), catalog, schema, getTables(metaData, catalog, schema));
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

        List<Key> importedKeys = getImportedKeys(metaData, catalog, schema, tableName);

        Map<String, List<Key>> map = new HashMap<>();

        for (Key key : importedKeys) {

            List<Key> keys;
            if (map.containsKey(key.name())) {
                keys = map.get(key.name());
            } else {
                keys = new ArrayList<>();
            }

            keys.add(key);
            map.put(key.name(), keys);
        }

        List<ForeignKey> foreignKeys = new ArrayList<>();

        for (String keyName : map.keySet()) {

            List<Key> keys = map.get(keyName);
            keys.sort(Comparator.comparing(Key::sequence));

            List<KeyColumn> columns = new ArrayList<>();

            String primaryKeyTable = null;
            for (Key key : keys) {
                primaryKeyTable = key.primaryTableName();
                columns.add(new KeyColumn(key.sequence(), getColumn(metaData, catalog, schema, tableName, key.foreignColumnName())));
            }

            foreignKeys.add(new ForeignKey(columns, getPrimaryKey(metaData, catalog, schema, primaryKeyTable)));

        }

        return foreignKeys;

    }

    private static List<Key> getImportedKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        List<Key> keys = new ArrayList<>();

        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {

            while (rs.next()) {
                String primaryKeyTableName = rs.getString("PKTABLE_NAME");
                String primaryKeyColumnName = rs.getString("PKCOLUMN_NAME");
                String fkTableName = rs.getString("FKTABLE_NAME");
                String fkColumnName = rs.getString("FKCOLUMN_NAME");
                int seq = rs.getInt("KEY_SEQ");
                String fkName = rs.getString("FK_NAME");

                keys.add(new Key(primaryKeyTableName, primaryKeyColumnName, fkTableName, fkColumnName, seq, fkName));
            }
        }

        return keys;
    }


    private static List<Key> getExportedKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        List<Key> keys = new ArrayList<>();

        try (ResultSet rs = metaData.getExportedKeys(catalog, schema, tableName)) {

            while (rs.next()) {
                String primaryKeyTableName = rs.getString("PKTABLE_NAME");
                String primaryKeyColumnName = rs.getString("PKCOLUMN_NAME");
                String fkTableName = rs.getString("FKTABLE_NAME");
                String fkColumnName = rs.getString("FKCOLUMN_NAME");
                int seq = rs.getInt("KEY_SEQ");
                String pkName = rs.getString("PK_NAME");

                keys.add(new Key(primaryKeyTableName, primaryKeyColumnName, fkTableName, fkColumnName, seq, pkName));
            }
        }

        return keys;
    }


    private static PrimaryKey getPrimaryKey(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        try (ResultSet primaryKeyResultSet = metaData.getPrimaryKeys(catalog, schema, tableName)) {

            List<KeyColumn> keyColumns = new ArrayList<>();

            while (primaryKeyResultSet.next()) {

                String columnName = primaryKeyResultSet.getString("COLUMN_NAME");
                int sequence = primaryKeyResultSet.getInt("KEY_SEQ");

                keyColumns.add(new KeyColumn(sequence, getColumn(metaData, catalog, schema, tableName, columnName)));
            }

            keyColumns.sort(Comparator.comparing(KeyColumn::sequence));

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
        List<ForeignKey> foreignKeys = table.foreignKeys();

        if (foreignKeys != null && !foreignKeys.isEmpty()) {

            for (ForeignKey foreignKey : foreignKeys) {

                // get the columns on this table specified in the foreign key
                List<KeyColumn> keyColumns = foreignKey.foreignKeyColumns();

                for (KeyColumn keyColumn : keyColumns) {
                    int keyColumnSequence = keyColumn.sequence();

                    // this is a column on this table
                    Column foreignKeyColumn = keyColumn.column();

                    if (foreignKeyColumn.equals(column)) {

                        // for the column on this table that is a foreign key, grab the associated column the another table where it is the primary key
                        PrimaryKey primaryKey = foreignKey.primaryKey();

                        // for the primary key grab its full table data
                        Table primaryTable = database.getTable(primaryKey.tableName());

                        for (KeyColumn primaryKeyColumn : primaryKey.keyColumns()) {

                            int primaryKeyColumnSequence = primaryKeyColumn.sequence();

                            // let's make sure we are matching the right column in sequence
                            if (primaryKeyColumnSequence == keyColumnSequence) {

                                Column primaryKeyColumnColumn = primaryKeyColumn.column();

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