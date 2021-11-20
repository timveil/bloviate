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

package io.bloviate.file;

import io.bloviate.gen.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class FlatFileTest {

    @Test
    public void generate() {

        List<ColumnDefinition> definitions = new ArrayList<>();

        Random random = new Random();

        definitions.add(new ColumnDefinition("big_decimal_col", new BigDecimalGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("bit_col", new BitGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("varbit_col", new BitStringGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("boolean_col", new BooleanGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("byte_col", new ByteGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("char_col", new CharacterGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("date_col", new DateGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("double_col", new DoubleGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("float_col", new FloatGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("integer_col", new IntegerGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("long_col", new LongGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("short_col", new ShortGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("string_col", new SimpleStringGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("sql_date_col", new SqlDateGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("sql_time_col", new SqlTimeGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("sql_timestamp_col", new SqlTimestampGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("sql_uuid_col", new UUIDGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("instant_col", new InstantGenerator.Builder(random).build()));
        definitions.add(new ColumnDefinition("interval_col", new IntervalGenerator.Builder(random).build()));

        // need to implement as JsonbGenerator still returns null
        //definitions.add(new ColumnDefinition("jsonb_col", new JsonbGenerator.Builder().build()));

        FlatFileGenerator csv = new FlatFileGenerator.Builder("target/csv-test").addAll(definitions).build();
        csv.generate();
        csv.yaml();

        FlatFileGenerator tab = new FlatFileGenerator.Builder("target/tab-test").output(new TabDelimitedFile()).addAll(definitions).build();
        tab.generate();
        tab.yaml();

        FlatFileGenerator pipe = new FlatFileGenerator.Builder("target/pipe-test").output(new PipeDelimitedFile()).addAll(definitions).build();
        pipe.generate();
        pipe.yaml();

        //new FlatFile.Builder("target/large-csv-test").output(new CsvFile()).addAll(definitions).rows(1000000).build().generate();
    }
}