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
 * The process exit codes of the {@code bloviate} command, documented in every command's
 * {@code --help}.
 */
public final class ExitCodes {

    /** The command did what it was asked to. */
    public static final int OK = 0;

    /** The fill, or a {@code --before}/{@code --after} script, failed with a SQL error. */
    public static final int FILL_FAILED = 1;

    /** The command line or the configuration is wrong: nothing was connected to or written. */
    public static final int USAGE = 2;

    /** The database cannot be reached: it refused the connection, or no JDBC driver accepts the URL. */
    public static final int CONNECTION = 3;

    // The {@code key:description} lines picocli renders under "Exit codes". Constants, not an array,
    // because an annotation can only take compile-time constants.

    static final String HELP_OK = "0:Success";

    static final String HELP_FILL_FAILED =
            "1:Fill or hook failure (a SQL error while filling, or in a --before/--after script)";

    static final String HELP_USAGE =
            "2:Usage or configuration error (bad flag or value, a table pattern that matches nothing, "
                    + "a foreign key to an excluded table)";

    static final String HELP_CONNECTION =
            "3:Connection error (cannot connect, or no JDBC driver accepts the URL)";

    private ExitCodes() {
    }
}
