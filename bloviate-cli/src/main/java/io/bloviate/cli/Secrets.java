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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps passwords out of anything the CLI prints. A driver's error message often quotes the URL it was
 * given, and a URL can carry a password ({@code jdbc:postgresql://u:pw@host/db},
 * {@code ...?password=pw}, H2's {@code ;PASSWORD=pw}); the password from {@code BLOVIATE_PASSWORD} or a
 * password file can show up in a driver's message too. Every message and stack trace goes through
 * {@link #scrub(String)} on its way out.
 */
final class Secrets {

    /** What replaces a secret. */
    static final String MASK = "****";

    /** {@code password=...} / {@code pwd=...} query or property parameters; group 1 keeps the name. */
    private static final Pattern PARAMETER =
            Pattern.compile("(?i)([?&;,]\\s*(?:password|passwd|pwd)\\s*=)([^&;,\\s]*)");

    /** {@code //user:password@host} authority; group 1 keeps the {@code //user:} part. */
    private static final Pattern AUTHORITY = Pattern.compile("(//[^/:@\\s]*:)([^@/\\s]*)@");

    private final Set<String> values = new LinkedHashSet<>();

    /**
     * Collects every secret that could be quoted back: the password and any password the URL embeds.
     *
     * @param url      the JDBC URL as the user gave it; may be null
     * @param password the separately supplied password; may be null
     */
    Secrets(String url, String password) {
        add(password);
        if (url != null) {
            collect(PARAMETER, url);
            collect(AUTHORITY, url);
        }
    }

    /**
     * No secrets: only the URL patterns are masked.
     *
     * @return an instance that knows no password
     */
    static Secrets none() {
        return new Secrets(null, null);
    }

    private void collect(Pattern pattern, String url) {
        Matcher matcher = pattern.matcher(url);
        while (matcher.find()) {
            add(matcher.group(2));
        }
    }

    private void add(String value) {
        if (value != null && !value.isEmpty()) {
            values.add(value);
        }
    }

    /**
     * Masks the password in a JDBC URL, keeping the rest so it is still recognizable.
     *
     * @param url a JDBC URL
     * @return the URL with any embedded password replaced by {@value #MASK}
     */
    static String redactUrl(String url) {
        String masked = PARAMETER.matcher(url).replaceAll("$1" + MASK);
        return AUTHORITY.matcher(masked).replaceAll("$1" + MASK + "@");
    }

    /**
     * Masks every known secret, and any URL-shaped password, in a piece of text.
     *
     * @param text a message or stack trace about to be printed; may be null
     * @return the text without any password
     */
    String scrub(String text) {
        if (text == null) {
            return null;
        }
        String scrubbed = redactUrl(text);
        for (String value : values) {
            scrubbed = scrubbed.replace(value, MASK);
        }
        return scrubbed;
    }
}
