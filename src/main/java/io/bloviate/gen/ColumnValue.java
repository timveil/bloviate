package io.bloviate.gen;

import io.bloviate.db.metadata.Column;

public record ColumnValue<T>(Column column, T value) {
}
