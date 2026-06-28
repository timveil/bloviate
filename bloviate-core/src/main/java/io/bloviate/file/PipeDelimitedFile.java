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
 * {@link FileDefinition} for pipe-delimited output. Fields are separated by a
 * pipe character ({@code |}).
 */
public class PipeDelimitedFile extends FileDefinition {

    /**
     * Constructs a new pipe-delimited file definition using the {@link FileType#PIPE} format.
     */
    public PipeDelimitedFile() {
        super(FileType.PIPE);
    }
}
