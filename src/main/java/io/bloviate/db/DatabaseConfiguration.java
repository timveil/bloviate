package io.bloviate.db;

import io.bloviate.ext.DatabaseSupport;

public record DatabaseConfiguration(int batchSize, long defaultRowCount, DatabaseSupport databaseSupport) {
}
