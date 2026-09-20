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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits SQL script text into individual statements and substitutes {@code ${name}} tokens, in one
 * pass over the text. It understands enough lexical structure that a {@code ;} inside a string, a
 * quoted identifier, a comment or a dollar-quoted body does not end a statement:
 *
 * <ul>
 *   <li>single-quoted strings, with {@code ''} as the escape (and backslash escapes in
 *       {@code E'...'} strings, which PostgreSQL defines)</li>
 *   <li>double-quoted and backtick-quoted identifiers, with the doubled quote as the escape</li>
 *   <li>PostgreSQL dollar-quoted bodies, {@code $$...$$} and {@code $tag$...$tag$}</li>
 *   <li>{@code --} line comments and slash-star block comments, which nest as in PostgreSQL</li>
 * </ul>
 *
 * <p>Comments are removed from the returned statements (replaced by whitespace), except for the
 * slash-star-bang and slash-star-plus forms that MySQL and several optimizers treat as executable
 * hints. A chunk of text that is only whitespace and comments yields no statement. Text after the
 * last {@code ;} is a statement in its own right.
 *
 * <p>Tokens are substituted everywhere except inside comments, so a value is spliced in as text after
 * the statement boundaries have been found &mdash; a value containing {@code ;} cannot split a
 * statement. A token with no value fails the split, naming the token and its line.
 *
 * <p>Not supported: the MySQL client's {@code DELIMITER} command (fails with a clear message rather
 * than mis-splitting), and backslash escapes in ordinary quoted strings (standard SQL, and the
 * PostgreSQL default, treat the backslash as an ordinary character).
 */
final class SqlScriptSplitter {

    /** {@code ${name}} where the name is an identifier-like word. */
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}");

    /** One statement: its 1-based number among the non-empty statements, its first line, its text. */
    record Statement(int number, int line, String sql) {
    }

    private final String text;
    private final int length;
    private final Function<String, String> tokens;

    private final List<Statement> statements = new ArrayList<>();
    // AvoidStringBufferField: this scanner is a short-lived, single-use object (one per split call,
    // garbage once the statements are returned), so the buffer cannot outlive the script it holds.
    @SuppressWarnings("PMD.AvoidStringBufferField")
    private final StringBuilder current = new StringBuilder();
    private int position;
    private int line = 1;
    private int startLine;
    private boolean significant;

    private SqlScriptSplitter(String text, Function<String, String> tokens) {
        this.text = text;
        this.length = text.length();
        this.tokens = tokens;
    }

    /**
     * Splits {@code script} into statements.
     *
     * @param script the script text
     * @param tokens resolves a token name to its replacement text, or {@code null} when unresolved
     * @return the non-empty statements in order
     * @throws IllegalArgumentException on an unterminated string, comment or dollar-quoted body, an
     *                                  unresolved token, or a {@code DELIMITER} command
     */
    static List<Statement> split(String script, Function<String, String> tokens) {
        return new SqlScriptSplitter(script, tokens).run();
    }

    private List<Statement> run() {
        while (position < length) {
            char c = text.charAt(position);
            if (c == ';') {
                position++;
                endStatement();
            } else if (c == '\'') {
                quoted('\'', isEscapeString());
            } else if (c == '"' || c == '`') {
                quoted(c, false);
            } else if (c == '-' && peek(1) == '-') {
                lineComment();
            } else if (c == '/' && peek(1) == '*') {
                blockComment();
            } else if (c == '$') {
                dollar();
            } else {
                plain(c);
            }
        }
        endStatement();
        return statements;
    }

    private char peek(int ahead) {
        int index = position + ahead;
        return index < length ? text.charAt(index) : '\0';
    }

    /** Appends one ordinary character, tracking the statement's first significant line. */
    private void plain(char c) {
        if (!Character.isWhitespace(c)) {
            markSignificant();
        }
        if (c == '\n') {
            line++;
        }
        current.append(c);
        position++;
    }

    private void markSignificant() {
        if (!significant) {
            significant = true;
            startLine = line;
        }
    }

    /** True when the quote at {@link #position} opens a PostgreSQL {@code E'...'} escape string. */
    private boolean isEscapeString() {
        if (position == 0) {
            return false;
        }
        char before = text.charAt(position - 1);
        return (before == 'E' || before == 'e') && (position < 2 || !isIdentifierPart(text.charAt(position - 2)));
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** Copies a quoted string or identifier through its closing quote. */
    private void quoted(char quote, boolean backslashEscapes) {
        int end = position + 1;
        while (true) {
            if (end >= length) {
                throw new IllegalArgumentException("unterminated " + describeQuote(quote) + " starting at line " + line);
            }
            char c = text.charAt(end);
            if (backslashEscapes && c == '\\') {
                end += 2;
            } else if (c == quote) {
                if (end + 1 < length && text.charAt(end + 1) == quote) {
                    end += 2;
                } else {
                    break;
                }
            } else {
                end++;
            }
        }
        appendSegment(position, end + 1);
    }

    private static String describeQuote(char quote) {
        return switch (quote) {
            case '\'' -> "string literal";
            case '`' -> "backtick-quoted identifier";
            default -> "quoted identifier";
        };
    }

    private void lineComment() {
        int end = text.indexOf('\n', position);
        position = end < 0 ? length : end;
        // whitespace so the tokens either side of the comment cannot fuse; the newline itself is
        // consumed by the next plain() call
        current.append(' ');
    }

    private void blockComment() {
        int end = position + 2;
        int depth = 1;
        while (depth > 0) {
            if (end >= length) {
                throw new IllegalArgumentException("unterminated block comment starting at line " + line);
            }
            if (text.startsWith("/*", end)) {
                depth++;
                end += 2;
            } else if (text.startsWith("*/", end)) {
                depth--;
                end += 2;
            } else {
                end++;
            }
        }
        char kind = peek(2);
        if (kind == '!' || kind == '+') {
            // an executable comment (MySQL) or optimizer hint: part of the statement, keep it
            appendSegment(position, end);
        } else {
            line += newlines(text.substring(position, end), end - position);
            position = end;
            current.append(' ');
        }
    }

    /** Handles a {@code ${token}}, a dollar-quoted body, or a literal {@code $}. */
    private void dollar() {
        if (peek(1) == '{') {
            Matcher matcher = TOKEN.matcher(text).region(position, length);
            if (matcher.lookingAt()) {
                appendSegment(position, matcher.end());
                return;
            }
        }
        String tag = position == 0 || !isIdentifierPart(text.charAt(position - 1)) ? dollarTag() : null;
        if (tag == null) {
            plain('$');
            return;
        }
        int close = text.indexOf(tag, position + tag.length());
        if (close < 0) {
            throw new IllegalArgumentException("unterminated dollar-quoted body " + tag + " starting at line " + line);
        }
        appendSegment(position, close + tag.length());
    }

    /** The dollar-quote tag ({@code $$} or {@code $name$}) opening at {@link #position}, else null. */
    private String dollarTag() {
        int end = position + 1;
        if (end < length && (Character.isLetter(text.charAt(end)) || text.charAt(end) == '_')) {
            while (end < length && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
                end++;
            }
        }
        return end < length && text.charAt(end) == '$' ? text.substring(position, end + 1) : null;
    }

    /**
     * Appends {@code text[from, to)} to the current statement with tokens substituted, and moves the
     * scan past it. Used for every span that is not comment or plain text, so quoted text and
     * dollar-quoted bodies take part in token substitution.
     */
    private void appendSegment(int from, int to) {
        String segment = text.substring(from, to);
        markSignificant();
        Matcher matcher = TOKEN.matcher(segment);
        int copied = 0;
        while (matcher.find()) {
            current.append(segment, copied, matcher.start());
            String name = matcher.group(1);
            String value = tokens.apply(name);
            if (value == null) {
                throw new IllegalArgumentException("unresolved token ${" + name + "} at line "
                        + (line + newlines(segment, matcher.start())));
            }
            current.append(value);
            copied = matcher.end();
        }
        current.append(segment, copied, segment.length());
        line += newlines(segment, segment.length());
        position = to;
    }

    /** The number of line breaks in {@code segment[0, to)}. */
    private static int newlines(String segment, int to) {
        int count = 0;
        for (int i = 0; i < to; i++) {
            if (segment.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private void endStatement() {
        String sql = current.toString().strip();
        boolean keep = significant;
        current.setLength(0);
        significant = false;
        if (!keep) {
            return;
        }
        if (isDelimiterCommand(sql)) {
            throw new IllegalArgumentException("the MySQL client command DELIMITER (line " + startLine
                    + ") is not supported; terminate every statement with ';' (a stored routine body "
                    + "containing ';' cannot be split without it, so run that one through the driver directly)");
        }
        statements.add(new Statement(statements.size() + 1, startLine, sql));
    }

    private static boolean isDelimiterCommand(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        return lower.startsWith("delimiter") && (lower.length() == 9 || Character.isWhitespace(lower.charAt(9)));
    }
}
