package io.bloviate.db;

import io.bloviate.ext.DatabaseSupport;

import java.util.Set;

public record DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport, Set<TableConfiguration> tableConfigurations) {

    public TableConfiguration tableConfiguration(String tableName) {
        if (tableConfigurations != null && !tableConfigurations.isEmpty()) {
            for (TableConfiguration tableConfiguration : tableConfigurations) {
                if (tableConfiguration.tableName().equalsIgnoreCase(tableName)) {
                    return tableConfiguration;
                }
            }
        }

        return null;
    }
}
