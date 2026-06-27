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
 * Abstract base class for defining file format specifications.
 * Subclasses of this class define specific file formats (CSV, TSV, pipe-delimited)
 * for data export.
 *
 * @since 1.0.0
 */
public abstract class FileDefinition {

    private final FileType fileType;

    /**
     * Constructs a new FileDefinition with the specified file type.
     *
     * @param fileType the type of file format this definition represents
     */
    public FileDefinition(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Returns the file type of this definition.
     *
     * @return the FileType enum value representing the file format
     */
    public FileType getFileType() {
        return fileType;
    }

}
