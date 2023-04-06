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

package io.bloviate.db.metadata;

import java.sql.JDBCType;
import java.util.Comparator;
import java.util.Objects;

public record Column(String name, String tableName, String schema, String catalog, JDBCType jdbcType, Integer maxSize, Integer maxDigits, String typeName, Boolean autoIncrement, Boolean nullable, String defaultValue, Integer ordinalPosition) implements Comparable<Column> {

    @Override
    public int compareTo(Column that) {
        return Objects.compare(this, that, Comparator.comparing(Column::ordinalPosition));
    }
}
