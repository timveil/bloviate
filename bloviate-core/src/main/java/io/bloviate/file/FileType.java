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
 * Enumeration of supported file types for data export.
 * Each file type defines its file extension; the actual delimiter is applied by
 * {@link FlatFileGenerator} via Commons CSV {@code CSVFormat}.
 *
 * @since 1.0.0
 */
public enum FileType {
    /**
     * Comma-separated values format.
     */
    CSV("csv"),

    /**
     * Tab-delimited values format.
     */
    TDV("txt"),

    /**
     * Pipe-delimited values format.
     */
    PIPE("txt");

    private final String extension;

    /**
     * Constructs a FileType with the specified extension.
     *
     * @param extension the file extension for this format
     */
    FileType(String extension) {
        this.extension = extension;
    }

    /**
     * Returns the file extension for this file type.
     *
     * @return the file extension (without the dot)
     */
    public String getExtension() {
        return extension;
    }
}
