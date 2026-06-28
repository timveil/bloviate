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

import java.sql.SQLException;

/**
 * Contract for components that populate a target with generated data.
 *
 * <p>A {@code Fillable} drives a one-shot fill operation: invoking {@link #fill()} runs the work to
 * completion (or fails), rather than returning a stream or builder. The two engine entry points
 * implement it — {@link DatabaseFiller} fills an entire database in dependency order and
 * {@link TableFiller} fills a single table — so callers can treat either uniformly.
 *
 * @since 1.0.0
 */
public interface Fillable {

    /**
     * Fills the target with generated data, running the operation to completion.
     *
     * @throws SQLException if any database access error occurs during the fill operation
     */
    void fill() throws SQLException;
}
