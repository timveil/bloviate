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

import io.bloviate.db.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Utility class for extracting database metadata through JDBC.
 * 
 * <p>DatabaseUtils provides static methods for analyzing database structure
 * and relationships by interrogating JDBC metadata. It converts raw JDBC
 * metadata into structured {@link Database}, {@link Table}, {@link Column},
 * and key relationship objects.
 * 
 * <p>Key capabilities include:
 * <ul>
 *   <li>Database metadata extraction from connections or data sources</li>
 *   <li>Table discovery and column analysis</li>
 *   <li>Primary and foreign key relationship mapping</li>
 *   <li>Foreign key chain traversal for data generation dependencies</li>
 * </ul>
 * 
 * <p>The extracted metadata is used by {@link DatabaseFiller} to understand
 * table dependencies and generate appropriate test data that respects
 * referential integrity constraints.
 * 
 * @author Tim Veil
 * @see Database
 * @see Table
 * @see Column
 * @see DatabaseFiller
 */
public class DatabaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtils.class);

    /** Static utility holder — not instantiable. */
    private DatabaseUtils() {
    }

    /**
     * Computes a stable, reproducible generation seed for a column.
     *
     * <p>The seed combines a caller-supplied base seed (from
     * {@link io.bloviate.db.DatabaseConfiguration#seed()}) with a deterministic hash of the
     * column's identity. The identity intentionally uses the {@link JDBCType} <em>name</em>
     * rather than the enum constant: {@code Enum.hashCode()} is identity-based and therefore
     * varies between JVM runs, which would make generated data non-reproducible. Every component
     * used here ({@link String}, {@link Integer}, and the type name) has a hash that is stable
     * across runs, so the same schema and base seed always yield the same data.
     *
     * <p>Because this is a pure function of the column, a foreign-key column seeded from its
     * associated primary-key column resolves to the same seed the primary key itself uses,
     * preserving referential fidelity.
     *
     * @param column   the column to derive a seed for
     * @param baseSeed the configured base seed; vary it to produce a different but still
     *                 reproducible dataset
     * @return the seed to construct the column's generator with
     */
    public static long columnSeed(Column column, long baseSeed) {
        int identity = Objects.hash(
                column.name(),
                column.tableName(),
                column.schema(),
                column.catalog(),
                column.jdbcType() == null ? null : column.jdbcType().getName(),
                column.ordinalPosition());
        return baseSeed * 1_000_003L + identity;
    }

    /**
     * Extracts complete database metadata from a DataSource.
     * 
     * @param dataSource the data source to analyze
     * @return a Database object containing all discovered metadata
     * @throws SQLException if database access fails
     */
    public static Database getMetadata(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return getMetadata(connection);
        }
    }

    /**
     * Extracts complete database metadata from a Connection.
     * 
     * <p>Analyzes the database structure including tables, columns, primary keys,
     * and foreign key relationships. The resulting Database object provides a
     * complete view of the database schema suitable for data generation planning.
     * 
     * @param connection the database connection to analyze
     * @return a Database object containing all discovered metadata
     * @throws SQLException if database access fails
     */
    public static Database getMetadata(Connection connection) throws SQLException {
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();

        DatabaseMetaData metaData = connection.getMetaData();

        return new Database(metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion(), catalog, schema, getTables(metaData, catalog, schema));
    }

    private static List<Table> getTables(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {

        List<String> tableNames = new ArrayList<>();

        try (ResultSet tablesResultSet = metaData.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (tablesResultSet.next()) {
                tableNames.add(tablesResultSet.getString("TABLE_NAME"));
            }
        }

        // each table's columns are fetched once and reused for primary- and foreign-key
        // resolution below, instead of issuing a getColumns round trip per key column
        Map<String, List<Column>> columnsByTable = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            columnsByTable.put(tableName, getColumns(metaData, catalog, schema, tableName));
        }

        // a table's primary key is needed once for itself and once per referencing foreign
        // key; cache it so each is read from the catalog a single time
        Map<String, PrimaryKey> primaryKeysByTable = new HashMap<>();

        List<Table> tables = new ArrayList<>();

        for (String tableName : tableNames) {
            PrimaryKey primaryKey = primaryKeyFor(metaData, catalog, schema, tableName, columnsByTable, primaryKeysByTable);
            List<ForeignKey> foreignKeys = getForeignKeys(metaData, catalog, schema, tableName, columnsByTable, primaryKeysByTable);

            tables.add(new Table(tableName, primaryKey, columnsByTable.get(tableName), foreignKeys));
        }

        return tables;
    }

    private static List<ForeignKey> getForeignKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName,
                                                   Map<String, List<Column>> columnsByTable,
                                                   Map<String, PrimaryKey> primaryKeysByTable) throws SQLException {

        List<Key> importedKeys = getImportedKeys(metaData, catalog, schema, tableName);

        // LinkedHashMap keeps foreign keys in driver-reported order. For a column participating in
        // MULTIPLE foreign keys, the first group in this order decides which parent seeds its
        // generator, so the order must be identical on every run and every JDK (hash order is
        // JDK-implementation-dependent and would violate within-version reproducibility). Note:
        // this ordering differs from releases <= 2.18.5, which used hash order — a deliberate,
        // release-noted change affecting only multi-FK columns.
        Map<String, List<Key>> map = new LinkedHashMap<>();

        for (Key key : importedKeys) {
            map.computeIfAbsent(key.name(), k -> new ArrayList<>()).add(key);
        }

        List<ForeignKey> foreignKeys = new ArrayList<>();

        for (List<Key> keys : map.values()) {

            keys.sort(Comparator.comparing(Key::sequence));

            List<KeyColumn> columns = new ArrayList<>();

            String primaryKeyTable = null;
            for (Key key : keys) {
                primaryKeyTable = key.primaryTableName();
                columns.add(new KeyColumn(key.sequence(), columnFor(metaData, catalog, schema, tableName, key.foreignColumnName(), columnsByTable)));
            }

            foreignKeys.add(new ForeignKey(columns, primaryKeyFor(metaData, catalog, schema, primaryKeyTable, columnsByTable, primaryKeysByTable)));

        }

        return foreignKeys;

    }

    private static PrimaryKey primaryKeyFor(DatabaseMetaData metaData, String catalog, String schema, String tableName,
                                            Map<String, List<Column>> columnsByTable,
                                            Map<String, PrimaryKey> primaryKeysByTable) throws SQLException {
        PrimaryKey cached = primaryKeysByTable.get(tableName);
        if (cached != null) {
            return cached;
        }

        PrimaryKey primaryKey = getPrimaryKey(metaData, catalog, schema, tableName, columnsByTable);
        primaryKeysByTable.put(tableName, primaryKey);
        return primaryKey;
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


    private static PrimaryKey getPrimaryKey(DatabaseMetaData metaData, String catalog, String schema, String tableName,
                                            Map<String, List<Column>> columnsByTable) throws SQLException {

        try (ResultSet primaryKeyResultSet = metaData.getPrimaryKeys(catalog, schema, tableName)) {

            List<KeyColumn> keyColumns = new ArrayList<>();

            while (primaryKeyResultSet.next()) {

                String columnName = primaryKeyResultSet.getString("COLUMN_NAME");
                int sequence = primaryKeyResultSet.getInt("KEY_SEQ");

                keyColumns.add(new KeyColumn(sequence, columnFor(metaData, catalog, schema, tableName, columnName, columnsByTable)));
            }

            keyColumns.sort(Comparator.comparing(KeyColumn::sequence));

            return new PrimaryKey(tableName, keyColumns);

        }

    }

    private static Column columnFor(DatabaseMetaData metaData, String catalog, String schema, String tableName, String columnName,
                                    Map<String, List<Column>> columnsByTable) throws SQLException {

        List<Column> columns = columnsByTable.get(tableName);

        if (columns == null) {
            // a key can reference a table outside the introspected set (e.g. a view filter or
            // another schema); load its columns once and reuse them for later references
            columns = getColumns(metaData, catalog, schema, tableName);
            columnsByTable.put(tableName, columns);
        }

        for (Column column : columns) {
            if (column.name().equals(columnName)) {
                return column;
            }
        }

        // fall back to a case-insensitive match: key result sets and column result sets can
        // disagree on identifier case with some drivers
        for (Column column : columns) {
            if (column.name().equalsIgnoreCase(columnName)) {
                return column;
            }
        }

        throw new IllegalStateException(String.format("can't find column in table [%s] with name [%s]", tableName, columnName));
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

    /**
     * Traverses foreign key relationships to find the root primary key column.
     * 
     * <p>Given a column that may be part of a foreign key, this method follows
     * the foreign key chain to find the ultimate primary key column that should
     * be used as the source for generating related data values.
     * 
     * <p>This is particularly useful for ensuring referential integrity when
     * generating test data across related tables.
     * 
     * @param database the database containing all table metadata
     * @param table the table containing the column to analyze
     * @param column the column to find the associated primary key for
     * @return the root primary key column, or null if no foreign key relationship exists
     */
    public static Column getAssociatedPrimaryKeyColumn(Database database, Table table, Column column) {
        return getAssociatedPrimaryKeyColumn(database, table, column, new HashSet<>());
    }

    /**
     * Cycle-safe implementation of {@link #getAssociatedPrimaryKeyColumn(Database, Table, Column)}.
     * {@code visited} accumulates the columns already resolved on the current foreign-key chain (a
     * {@link Column} carries its owning table name, so it identifies a table/column pair); if one
     * recurs (a circular FK schema, e.g. A&rarr;B&rarr;A) the traversal stops and returns {@code null}
     * instead of recursing without bound to a {@link StackOverflowError}.
     */
    private static Column getAssociatedPrimaryKeyColumn(Database database, Table table, Column column, Set<Column> visited) {

        if (!visited.add(column)) {
            logger.warn("circular foreign-key reference detected at [{}.{}]; stopping primary-key resolution",
                    table.name(), column.name());
            return null;
        }

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

                                Column possibleRoot = getAssociatedPrimaryKeyColumn(database, primaryTable, primaryKeyColumnColumn, visited);

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