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

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reports the project version for {@code --version}, read from a resource Maven filters at build time.
 * A resource rather than the jar manifest, so it is right when run from {@code target/classes} too.
 */
public final class VersionProvider implements IVersionProvider {

    private static final String RESOURCE = "version.properties";

    /** What {@code --version} reports when the resource is missing or was never filtered (an IDE run). */
    static final String UNKNOWN = "unknown";

    /**
     * Reads the version.
     *
     * @return the project version, or {@value #UNKNOWN}
     */
    static String version() {
        try (InputStream in = VersionProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", UNKNOWN).trim();
            return version.isEmpty() || version.startsWith("${") ? UNKNOWN : version;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    @Override
    public String[] getVersion() {
        return new String[]{BloviateCommand.NAME + " " + version()};
    }
}
