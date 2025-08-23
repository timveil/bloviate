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

package io.bloviate.file;

import io.bloviate.gen.DataGenerator;

/**
 * Defines a column for flat file generation.
 * This record pairs a column name with its corresponding data generator,
 * specifying how data should be generated for that column in the output file.
 *
 * @param name the name of the column
 * @param dataGenerator the generator used to produce values for this column
 * @since 1.0.0
 */
public record ColumnDefinition(String name, DataGenerator<?> dataGenerator) {}
