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
import io.bloviate.ext.DatabaseSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

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
        return getMetadata(connection, UnaryOperator.identity());
    }

    /**
     * Extracts database metadata from a Connection, keeping only the tables a filter selects.
     *
     * <p>The catalog and schema read are the connection's current ones. The filter receives the
     * names of every table found there and returns the names to keep, in the order to keep them; it
     * runs before any column or key metadata is read, so a large schema pays only for the tables it
     * keeps. A foreign key that references a table the filter dropped is still described (its parent's
     * columns are read on demand), but that table is not in {@link Database#tables()}; callers that
     * fill the result must check for that, as {@link DatabaseFiller} does.
     *
     * @param connection  the database connection to analyze
     * @param tableFilter maps the names of all tables found to the names to keep; may throw
     *                    {@link IllegalArgumentException} to reject the selection
     * @return a Database object containing the metadata of the selected tables
     * @throws SQLException if database access fails
     * @since 3.3.0
     */
    public static Database getMetadata(Connection connection, UnaryOperator<List<String>> tableFilter) throws SQLException {
        return getMetadata(connection, DatabaseSupport.forConnection(connection),
                (names, partitions) -> tableFilter.apply(names));
    }

    /**
     * Extracts database metadata from a Connection using a {@link DatabaseSupport} to decide which
     * relations are tables to fill, keeping only the tables a filter selects.
     *
     * <p>Besides {@code TABLE}, the support may ask for other JDBC table types
     * ({@link DatabaseSupport#discoveredTableTypes()}) and names the <em>partitions</em> of
     * declaratively partitioned tables ({@link DatabaseSupport#readPartitions}). Partitions are never
     * tables to fill: they are left out of the names the filter sees and out of
     * {@link Database#tables()}, and the partitioned table they belong to (the outermost one, for
     * multi-level partitioning) stands in for them. The partition names are handed to the filter so it
     * can report a selection that names one. A foreign key that references a partitioned table is
     * described once, against that table; the extra keys some drivers report against each of its
     * partitions are dropped.
     *
     * @param connection  the database connection to analyze
     * @param support     the database support that discovers tables and partitions
     * @param tableFilter maps the names of all tables found (partitions excluded) and the partition
     *                    names (partition to its top-level partitioned table) to the names to keep; may
     *                    throw {@link IllegalArgumentException} to reject the selection
     * @return a Database object containing the metadata of the selected tables
     * @throws SQLException if database access fails
     * @since 3.6.0
     */
    public static Database getMetadata(Connection connection, DatabaseSupport support,
                                       BiFunction<List<String>, Map<String, String>, List<String>> tableFilter) throws SQLException {
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();

        DatabaseMetaData metaData = connection.getMetaData();

        return new Database(metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion(), catalog, schema,
                getTables(connection, metaData, support, catalog, schema, tableFilter));
    }

    private static List<Table> getTables(Connection connection, DatabaseMetaData metaData, DatabaseSupport support,
                                         String catalog, String schema,
                                         BiFunction<List<String>, Map<String, String>, List<String>> tableFilter) throws SQLException {

        // partitions of a partitioned table are filled through it, never on their own
        Map<String, String> partitions = support.readPartitions(connection, schema);

        List<String> discoveredNames = new ArrayList<>();

        // getTables takes the schema as a LIKE pattern, so a schema whose name contains _ or %
        // would also match other schemas; MetadataPatterns narrows it back to the one name
        MetadataPatterns patterns = MetadataPatterns.forMetaData(metaData);

        try (ResultSet tablesResultSet = metaData.getTables(catalog, patterns.literal(schema), null,
                support.discoveredTableTypes().toArray(String[]::new))) {
            while (tablesResultSet.next()) {
                String tableName = tablesResultSet.getString("TABLE_NAME");
                if (!partitions.containsKey(tableName)) {
                    discoveredNames.add(tableName);
                }
            }
        }

        List<String> tableNames = tableFilter.apply(discoveredNames, partitions);

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
            List<ForeignKey> foreignKeys = getForeignKeys(metaData, catalog, schema, tableName, columnsByTable, partitions);

            tables.add(new Table(tableName, primaryKey, columnsByTable.get(tableName), foreignKeys));
        }

        return tables;
    }

    private static List<ForeignKey> getForeignKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName,
                                                   Map<String, List<Column>> columnsByTable,
                                                   Map<String, String> partitions) throws SQLException {

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

            if (referencesPartition(keys, map.values(), partitions, tableName)) {
                continue;
            }

            List<KeyColumn> columns = new ArrayList<>();

            String primaryKeyTable = null;
            String primaryKeySchema = null;
            String primaryKeyCatalog = null;
            for (Key key : keys) {
                primaryKeyTable = key.primaryTableName();
                primaryKeySchema = key.primaryTableSchema();
                primaryKeyCatalog = key.primaryTableCatalog();
                columns.add(new KeyColumn(key.sequence(), columnFor(metaData, catalog, schema, tableName, key.foreignColumnName(), columnsByTable)));
            }

            boolean otherSchema = differs(schema, primaryKeySchema);
            boolean otherCatalog = differs(catalog, primaryKeyCatalog);

            if (otherSchema || otherCatalog) {
                // the parent is in another schema/catalog. Resolving it by name here would read the
                // same-named table of THIS schema (or nothing), so it is described without key columns
                // and marked; DatabaseFiller reports it instead of filling against the wrong parent
                foreignKeys.add(new ForeignKey(columns, new PrimaryKey(primaryKeyTable, List.of()),
                        otherSchema ? primaryKeySchema : null, otherCatalog ? primaryKeyCatalog : null));
            } else {
                // the columns the constraint actually names (PKCOLUMN_NAME), not the parent's declared
                // primary key. They are the same for the common `references parent(id)` case, but a key
                // to a UNIQUE target, or to primary-key columns in another order, is only correct this
                // way; taken from the same rows as `columns`, the two lists pair up by construction.
                List<KeyColumn> referencedColumns = new ArrayList<>();
                for (Key key : keys) {
                    referencedColumns.add(new KeyColumn(key.sequence(),
                            columnFor(metaData, catalog, schema, key.primaryTableName(), key.primaryColumnName(), columnsByTable)));
                }
                foreignKeys.add(new ForeignKey(columns, new PrimaryKey(primaryKeyTable, referencedColumns)));
            }

        }

        return foreignKeys;

    }

    /**
     * Whether a foreign key (one group of {@code keys} sharing a name) is one of the copies some drivers
     * report for a foreign key that references a partitioned table: PostgreSQL clones the constraint onto
     * every partition of the referenced table, and the driver lists each clone as a foreign key of its
     * own. Only the original, against the partitioned table itself, is a relationship to fill; the copies
     * would point at partitions, which are not tables being filled.
     *
     * <p>A copy is a foreign key to a partition that is <em>identical</em> to another foreign key of the
     * same table to that partition's top-level table: the same referencing columns and the same
     * referenced columns, pairwise and in the same order, in the same referenced schema and catalog
     * (a clone is the original re-pointed at a partition, so only the table differs). Comparing the
     * referencing columns alone would mistake a foreign key the user aimed at a partition for a copy
     * whenever the same referencing column also references the parent, for example
     * {@code child.a -> root(id)} beside {@code child.a -> partition(code)}. The metadata carries no
     * update/delete rules to compare, so a direct foreign key that mirrors the parent's exactly (same
     * columns against a same-named column of the partition) cannot be told from a copy and is dropped.
     *
     * <p>A foreign key to a partition with no such original is the user's own, aimed at a partition
     * directly; it cannot be honoured (a partition is never filled), so it is kept (the fill then fails
     * clearly, saying the referenced table is not being filled) and logged.
     */
    private static boolean referencesPartition(List<Key> keys, Collection<List<Key>> allKeys, Map<String, String> partitions,
                                               String tableName) {
        Key first = keys.getFirst();
        String referenced = first.primaryTableName();
        String root = partitions.get(referenced);
        if (root == null) {
            return false;
        }
        for (List<Key> other : allKeys) {
            Key otherFirst = other.getFirst();
            if (root.equals(otherFirst.primaryTableName())
                    && Objects.equals(first.primaryTableSchema(), otherFirst.primaryTableSchema())
                    && Objects.equals(first.primaryTableCatalog(), otherFirst.primaryTableCatalog())
                    && columnPairs(keys).equals(columnPairs(other))) {
                return true;
            }
        }
        logger.warn("table [{}] has a foreign key [{}] that references [{}], a partition of [{}]; a partition is never filled on "
                + "its own, so this key cannot be honoured. Reference the partitioned table instead",
                tableName, first.name(), referenced, root);
        return false;
    }

    /** The (referencing column, referenced column) pairs of one foreign key, in key-sequence order. */
    private static List<List<String>> columnPairs(List<Key> keys) {
        return keys.stream()
                .sorted(Comparator.comparing(Key::sequence))
                .map(key -> List.of(key.foreignColumnName(), key.primaryColumnName()))
                .toList();
    }

    /**
     * Whether a referenced table's schema or catalog is a different one from the one being read. Only a
     * definite difference counts: a null on either side (a driver that has no such concept, MySQL
     * reporting no schema, a connection with no current schema) is not a difference, and names compare
     * case-insensitively because key and table result sets can disagree on identifier case.
     */
    private static boolean differs(String selected, String referenced) {
        return selected != null && referenced != null && !selected.equalsIgnoreCase(referenced);
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
                String primaryKeySchema = rs.getString("PKTABLE_SCHEM");
                String primaryKeyCatalog = rs.getString("PKTABLE_CAT");

                keys.add(new Key(primaryKeyTableName, primaryKeyColumnName, fkTableName, fkColumnName, seq, fkName,
                        primaryKeySchema, primaryKeyCatalog));
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

    /**
     * Reads one table's columns.
     *
     * <p>{@code getColumns} takes the schema and the table name as LIKE patterns, so both are escaped:
     * unescaped, a table named {@code order_items} would also match {@code orderXitems} and this list
     * would carry the other table's columns too. The escaper is read per call rather than threaded
     * through the metadata read: every driver answers {@code getSearchStringEscape()} from a constant,
     * and this method already issues a catalog query.
     */
    private static List<Column> getColumns(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {

        MetadataPatterns patterns = MetadataPatterns.forMetaData(metaData);

        try (ResultSet columnsResultSet = metaData.getColumns(catalog, patterns.literal(schema), patterns.literal(tableName), null)) {

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
     * Traverses foreign key relationships to find the key column a column ultimately references.
     *
     * <p>Given a column that may be part of a foreign key, this follows the chain &mdash; through the
     * columns each key actually names, so a key to a {@code UNIQUE} target resolves correctly &mdash;
     * to the key column at the end of it. A chain that closes on itself stops there rather than
     * recursing without bound.
     *
     * <p>This describes the schema. It is <em>not</em> the rule the engine seeds by: a column shared by
     * several foreign keys has more than one chain, and {@link io.bloviate.db.ForeignKeyPlan} resolves
     * all of them together so the column's values satisfy every key at once. Use that to reason about
     * generated data; use this to ask what a key points at.
     *
     * @param database the database containing all table metadata
     * @param table the table containing the column to analyze, kept for source compatibility and not
     *        otherwise needed &mdash; a {@link Column} names its own table
     * @param column the column to find the associated key column for
     * @return the referenced key column, or null if no foreign key relationship exists
     * @throws IllegalArgumentException if a foreign key references a table that is not among the
     *         tables being filled
     */
    public static Column getAssociatedPrimaryKeyColumn(Database database, Table table, Column column) {
        return ForeignKeyPlan.of(database).referencedRoot(column);
    }

}
