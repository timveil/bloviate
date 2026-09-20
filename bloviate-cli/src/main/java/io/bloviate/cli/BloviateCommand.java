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

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * The top-level {@code bloviate} command: it does nothing itself, it groups the subcommands
 * ({@code fill} for now) and carries {@code --help} and {@code --version}. Running it with no
 * subcommand prints the usage.
 */
@Command(
        name = BloviateCommand.NAME,
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommands = FillCommand.class,
        description = "Fills JDBC databases with reproducible, foreign-key-aware test data.",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                ExitCodes.HELP_OK,
                ExitCodes.HELP_FILL_FAILED,
                ExitCodes.HELP_USAGE,
                ExitCodes.HELP_CONNECTION
        },
        footerHeading = "%nDrivers:%n",
        footer = {
                "  No JDBC driver is bundled. Put yours on the classpath and run the main",
                "  class ('java -jar' ignores -cp):",
                "    java -cp bloviate-cli.jar:postgresql.jar io.bloviate.cli.Main fill ..."
        })
public final class BloviateCommand implements Callable<Integer> {

    /** The program name shown in usage, version and error output. */
    static final String NAME = "bloviate";

    /** Set by picocli; used only to print the usage when no subcommand is given. */
    @Spec
    private CommandSpec spec;

    /**
     * No subcommand was given: print the usage (to stderr, like {@code git} does) and report it as a
     * usage error.
     *
     * @return {@link ExitCodes#USAGE}
     */
    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return ExitCodes.USAGE;
    }
}
