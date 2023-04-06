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

import java.util.List;

public record Database(String product, String productVersion, String catalog, String schema, List<Table> tables) {

    public Table getTable(String tableName) {
        for (Table table : tables) {
            if (table.name().equalsIgnoreCase(tableName)) {
                return table;
            }
        }

        throw new IllegalArgumentException(String.format("table with name [%s] not found", tableName));
    }

    @Override
    public String toString() {
        return "Database{" +
               "product='" + product + '\'' +
               ", productVersion='" + productVersion + '\'' +
               ", catalog='" + catalog + '\'' +
               ", schema='" + schema + '\'' +
               '}';
    }
}
