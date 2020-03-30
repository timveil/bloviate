/*
 * Copyright 2020 Tim Veil
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

package io.bloviate;

import io.bloviate.gen.DataGenerator;

public class ColumnDefinition {

    private final String header;
    private final DataGenerator dataGenerator;

    private String typeName;

    public ColumnDefinition(String header, String typeName, DataGenerator dataGenerator) {
        this.header = header;
        this.typeName = typeName;
        this.dataGenerator = dataGenerator;
    }

    public ColumnDefinition(String header, DataGenerator dataGenerator) {
        this.header = header;
        this.dataGenerator = dataGenerator;
    }


    public String getHeader() {
        return header;
    }

    public String getTypeName() {
        return typeName;
    }

    public DataGenerator getDataGenerator() {
        return dataGenerator;
    }
}
