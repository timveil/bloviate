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
 * Interface for objects that can be filled with generated data.
 * Implementations of this interface can populate database tables
 * or other data structures with synthetic data.
 *
 * @since 1.0.0
 */
public interface Fillable {

    /**
     * Fills the target with generated data.
     *
     * @throws SQLException if a database access error occurs during the fill operation
     */
    void fill() throws SQLException;
}
