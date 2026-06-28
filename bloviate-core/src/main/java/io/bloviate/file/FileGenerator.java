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

/**
 * Generates output files from a set of column definitions.
 *
 * <p>Implementations write generated data to disk and can also export their own
 * configuration as YAML.
 */
public interface FileGenerator {

    /**
     * Generates the output data file, writing one header row followed by the configured
     * number of data rows.
     */
    void generate();

    /**
     * Exports this generator's configuration to a YAML file (named after the generator's
     * base file name with a {@code .yaml} extension).
     */
    void yaml();
}
