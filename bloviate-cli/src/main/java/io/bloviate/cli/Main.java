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

import picocli.CommandLine;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.UnmatchedArgumentException;

import java.io.PrintWriter;
import java.util.Map;

/**
 * Entry point of the {@code bloviate} command line.
 *
 * <p>{@link #main(String[])} is the only place that terminates the JVM. Everything else is reachable
 * in-process through {@link #newCommandLine(Map)}, whose {@code execute(args)} returns the exit code, so
 * tests (and other Java callers) never need {@code System.exit}.
 */
public final class Main {

    private Main() {
    }

    /**
     * Runs the command line and exits the JVM with its exit code (see {@link ExitCodes}).
     *
     * @param args the command-line arguments
     */
    // the process exit code is the CLI's contract; every other entry point returns the code instead
    public static void main(String[] args) {
        System.exit(newCommandLine(System.getenv()).execute(args));
    }

    /**
     * Creates the {@code bloviate} command line, ready to {@link CommandLine#execute execute}.
     *
     * @param environment the environment variables the commands read (the password comes from
     *                    {@code BLOVIATE_PASSWORD}); {@link System#getenv()} in production
     * @return the configured command line
     */
    public static CommandLine newCommandLine(Map<String, String> environment) {
        IFactory factory = new EnvironmentFactory(environment);
        CommandLine commandLine = new CommandLine(new BloviateCommand(), factory);
        commandLine.setParameterExceptionHandler((error, arguments) -> handleParameterException(error));
        return commandLine;
    }

    /**
     * Reports a command-line syntax error the same way the commands report every other failure: one
     * {@code bloviate: <message>} line on stderr (plus picocli's "did you mean" suggestions), and the
     * usage-error exit code.
     */
    // CloseResource: false positive. The writer is the command line's own stderr, shared with everything
    // else that reports through it; flushing it is all that is wanted, closing it would silence later output.
    @SuppressWarnings("PMD.CloseResource")
    private static int handleParameterException(CommandLine.ParameterException error) {
        CommandLine commandLine = error.getCommandLine();
        PrintWriter err = commandLine.getErr();
        err.println(BloviateCommand.NAME + ": " + error.getMessage());
        UnmatchedArgumentException.printSuggestions(error, err);
        err.println("Try '" + commandLine.getCommandSpec().qualifiedName() + " --help' for usage.");
        err.flush();
        return ExitCodes.USAGE;
    }

    /** Hands the environment to the commands that read it; everything else is built the picocli way. */
    private static final class EnvironmentFactory implements IFactory {

        private final Map<String, String> environment;

        EnvironmentFactory(Map<String, String> environment) {
            this.environment = Map.copyOf(environment);
        }

        @Override
        public <K> K create(Class<K> type) throws Exception {
            if (type == FillCommand.class) {
                return type.cast(new FillCommand(environment));
            }
            return CommandLine.defaultFactory().create(type);
        }
    }
}
