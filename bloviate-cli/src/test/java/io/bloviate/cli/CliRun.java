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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * The outcome of running the CLI in-process: the exit code, and what the commands wrote to stdout and
 * stderr through picocli. Log lines are not in here (the logging binding writes to the JVM's own
 * stderr); a test that needs them runs the CLI in a child process, see {@link MainProcessTest}.
 *
 * @param code the exit code
 * @param out  stdout
 * @param err  stderr
 */
record CliRun(int code, String out, String err) {

    static CliRun run(String... args) {
        return run(Map.of(), args);
    }

    static CliRun run(Map<String, String> environment, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = Main.newCommandLine(environment);
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
        int code = commandLine.execute(args);
        return new CliRun(code, out.toString(), err.toString());
    }

    /** {@code fill --url <url> <extra args>}. */
    static CliRun fill(String url, String... extra) {
        return fill(Map.of(), url, extra);
    }

    static CliRun fill(Map<String, String> environment, String url, String... extra) {
        String[] args = new String[extra.length + 3];
        args[0] = "fill";
        args[1] = "--url";
        args[2] = url;
        System.arraycopy(extra, 0, args, 3, extra.length);
        return run(environment, args);
    }
}
