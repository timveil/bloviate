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

package io.bloviate.ext;

/**
 * An opaque token returned by {@link DatabaseSupport#disableConstraints} describing what was disabled,
 * so {@link DatabaseSupport#enableConstraints} can restore it.
 *
 * <p>The session-based PostgreSQL ({@code session_replication_role}) and MySQL
 * ({@code FOREIGN_KEY_CHECKS}) mechanisms restore state with a fixed {@code SET} statement and carry no
 * per-table state, so the handle is purely informational (a short {@code description} used for logging).
 * It exists so a future catalog-level mechanism (e.g. dropping and recreating constraints) can carry the
 * list of objects it must restore without changing the SPI signatures.
 *
 * @param description a short human-readable description of what was disabled, for logging
 * @since 2.17.0
 * @see DatabaseSupport#disableConstraints
 * @see DatabaseSupport#enableConstraints
 */
public record BulkLoadHandle(String description) {

    /**
     * Creates a handle with the given description.
     *
     * @param description a short human-readable description of what was disabled
     * @return the handle
     */
    public static BulkLoadHandle of(String description) {
        return new BulkLoadHandle(description);
    }
}
