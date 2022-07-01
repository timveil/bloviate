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

package io.bloviate.db;

import java.util.Set;

public record TableConfiguration(String tableName, long rowCount, Set<ColumnConfiguration> columnConfigurations) {

    public ColumnConfiguration columnConfiguration(String columnName) {
        if (columnConfigurations != null && !columnConfigurations.isEmpty()) {
            for (ColumnConfiguration columnConfiguration : columnConfigurations) {
                if (columnConfiguration.columnName().equalsIgnoreCase(columnName)) {
                    return columnConfiguration;
                }
            }
        }

        return null;
    }


}
