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

import java.sql.SQLException;

/**
 * Signals that a {@link DatabaseSupport} could not disable constraint enforcement for an
 * {@code UNORDERED_BULK} fill — for example, the connecting role lacks the privilege to set
 * PostgreSQL's {@code session_replication_role}.
 *
 * <p>{@link io.bloviate.db.DatabaseFiller} probes for this once before fanning out and, when it is
 * thrown, gracefully falls back to the ordered level-parallel fill path rather than running with
 * constraints half-disabled. It extends {@link SQLException} so it propagates naturally through the
 * fill API while remaining catchable as a distinct type.
 *
 * @since 2.17.0
 * @see DatabaseSupport#disableConstraints
 */
public class BulkLoadUnsupportedException extends SQLException {

    /**
     * Creates the exception with a message and underlying cause.
     *
     * @param message the detail message
     * @param cause   the underlying SQL failure (e.g. an insufficient-privilege error)
     */
    public BulkLoadUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
