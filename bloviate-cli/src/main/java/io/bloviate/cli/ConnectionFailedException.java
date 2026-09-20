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

package io.bloviate.cli;

/**
 * The database could not be reached: the connection was refused or rejected, or no JDBC driver on the
 * classpath accepts the URL. Kept apart from a {@link java.sql.SQLException} raised while filling so the
 * two get different exit codes ({@link ExitCodes#CONNECTION} and {@link ExitCodes#FILL_FAILED}).
 */
final class ConnectionFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what could not be reached, without any password
     * @param cause   the driver's own failure
     */
    ConnectionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
