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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SqlScriptSplitter}: statement boundaries, quoting and comment rules, token
 * substitution and the loud-failure cases. Pure text in, text out; no database.
 */
class SqlScriptSplitterTest {

    private static List<String> split(String script) {
        return split(script, Map.of());
    }

    private static List<String> split(String script, Map<String, String> tokens) {
        return SqlScriptSplitter.split(script, tokens::get).stream().map(SqlScriptSplitter.Statement::sql).toList();
    }

    @Test
    void splitsOnSemicolonsAndKeepsTrailingStatementWithoutOne() {
        assertEquals(List.of("select 1", "select 2", "select 3"), split("select 1;\nselect 2 ;select 3"));
    }

    @Test
    void skipsEmptyStatements() {
        assertEquals(List.of("select 1"), split(";; \n ;select 1;;\n;"));
        assertEquals(List.of(), split(""));
        assertEquals(List.of(), split("  \n\t "));
    }

    @Test
    void semicolonInsideSingleQuotedStringDoesNotSplit() {
        assertEquals(List.of("insert into t values ('a;b')", "select 2"),
                split("insert into t values ('a;b'); select 2"));
    }

    @Test
    void doubledQuoteIsAnEscapeInsideAString() {
        assertEquals(List.of("insert into t values ('it''s; fine')", "select 2"),
                split("insert into t values ('it''s; fine'); select 2"));
        assertEquals(List.of("select ''''"), split("select ''''"));
    }

    @Test
    void doubleQuotedAndBacktickIdentifiersMaySpanSemicolons() {
        assertEquals(List.of("select \"a;b\" from t", "select `c;d`, `e``;f` from u"),
                split("select \"a;b\" from t; select `c;d`, `e``;f` from u"));
        assertEquals(List.of("select \"say \"\"hi;\"\"\""), split("select \"say \"\"hi;\"\"\""));
    }

    @Test
    void backslashEscapesQuoteOnlyInEscapeStrings() {
        // E'...' honours \' so the string runs on to the last quote and holds the semicolon
        assertEquals(List.of("select E'it\\'s; ok'", "select 2"), split("select E'it\\'s; ok'; select 2"));
        // an ordinary string treats the backslash literally, so '\' is a complete string
        assertEquals(List.of("select '\\'", "select 2"), split("select '\\'; select 2"));
        // a trailing E belonging to an identifier is not an escape-string prefix
        assertEquals(List.of("select ne'\\'", "select 2"), split("select ne'\\'; select 2"));
    }

    @Test
    void lineCommentsAreRemovedAndMayContainSemicolonsAndQuotes() {
        String script = """
                -- header; with a semicolon and an unbalanced ' quote
                select 1; -- trailing; "comment
                select 2 -- last one, no newline""";
        assertEquals(List.of("select 1", "select 2"), split(script));
    }

    @Test
    void commentOnlyScriptHasNoStatements() {
        assertEquals(List.of(), split("-- nothing here;\n/* or here; */\n"));
    }

    @Test
    void dashesInsideStringsAreNotComments() {
        assertEquals(List.of("select '--not a comment; really'", "select 2"),
                split("select '--not a comment; really'; select 2"));
    }

    @Test
    void blockCommentsAreRemovedAndNest() {
        String script = "select /* a; 'b */ 1; /* outer /* inner; */ still outer; */ select 2";
        assertEquals(List.of("select   1", "select 2"), split(script));
    }

    @Test
    void commentsDoNotFuseNeighbouringTokens() {
        assertEquals(List.of("select a b"), split("select a/**/b"));
        assertEquals(List.of("select a \nb"), split("select a--x\nb"));
    }

    @Test
    void hintAndExecutableCommentsAreKept() {
        assertEquals(List.of("select /*+ INDEX(t i) */ 1", "/*!40101 SET x = 1 */"),
                split("select /*+ INDEX(t i) */ 1; /*!40101 SET x = 1 */"));
    }

    @Test
    void dollarQuotedBodyMayContainSemicolons() {
        String function = """
                create function f() returns int as $$
                begin
                  perform 1;
                  return 2;
                end;
                $$ language plpgsql""";
        assertEquals(List.of(function, "select f()"), split(function + ";\nselect f();"));
    }

    @Test
    void taggedDollarQuotesAreMatchedByTag() {
        String body = "create function g() returns text as $body$ select $$;$$ || ';' ; $body$ language sql";
        assertEquals(List.of(body, "select 2"), split(body + "; select 2"));
    }

    @Test
    void dollarSignsThatAreNotQuotesAreLiteral() {
        // positional parameter, identifier containing $, and a lone $
        assertEquals(List.of("select $1", "select a$b$ from t", "select 5 $ 3"),
                split("select $1; select a$b$ from t; select 5 $ 3"));
    }

    @Test
    void substitutesTokensInCodeStringsQuotedIdentifiersAndBodies() {
        Map<String, String> tokens = Map.of("schema", "reporting", "n", "7");
        assertEquals(List.of(
                        "insert into reporting.t values (7)",
                        "select 'reporting'",
                        "select \"reporting\".x",
                        "do $$ begin perform 7; end $$"),
                split("insert into ${schema}.t values (${n}); select '${schema}'; select \"${schema}\".x;"
                        + " do $$ begin perform ${n}; end $$", tokens));
    }

    @Test
    void tokenValueContainingSemicolonDoesNotSplit() {
        assertEquals(List.of("select 1; select 2"), split("${both}", Map.of("both", "select 1; select 2")));
    }

    @Test
    void tokensInsideCommentsAreNotSubstitutedOrRequired() {
        assertEquals(List.of("select 1"), split("-- uses ${nope}\n/* ${nope} */ select 1"));
    }

    @Test
    void tokenLikeTextThatIsNotATokenIsLeftAlone() {
        assertEquals(List.of("select '${a:-b}', '${', '$'"), split("select '${a:-b}', '${', '$'"));
    }

    @Test
    void unresolvedTokenNamesTheTokenAndItsLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> split("select 1;\nselect '\n${missing}'", Map.of("other", "x")));
        assertTrue(e.getMessage().contains("${missing}"), e.getMessage());
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
    }

    @Test
    void unresolvedTokenAtTopLevelReportsItsLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> split("select 1;\nselect ${missing}"));
        assertTrue(e.getMessage().contains("${missing}"), e.getMessage());
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void statementsCarryTheirNumberAndFirstLine() {
        List<SqlScriptSplitter.Statement> statements = SqlScriptSplitter.split(
                "-- header\n\nselect 1;\n\n  /* c\nc */ select\n 2;", name -> null);
        assertEquals(2, statements.size());
        assertEquals(new SqlScriptSplitter.Statement(1, 3, "select 1"), statements.get(0));
        assertEquals(2, statements.get(1).number());
        assertEquals(6, statements.get(1).line());
    }

    @Test
    void unterminatedConstructsFailLoudly() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> split("select 'abc")).getMessage()
                .contains("unterminated string literal"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> split("select \"abc")).getMessage()
                .contains("unterminated quoted identifier"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> split("select `abc")).getMessage()
                .contains("unterminated backtick"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> split("select 1 /* open /* nested */")).getMessage()
                .contains("unterminated block comment"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> split("do $x$ select 1; $y$")).getMessage()
                .contains("unterminated dollar-quoted body $x$"));
    }

    @Test
    void delimiterCommandIsRejectedWithAClearMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> split("select 1;\nDELIMITER //\ncreate procedure p() begin select 1; end//\nDELIMITER ;"));
        assertTrue(e.getMessage().contains("DELIMITER"), e.getMessage());
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
        assertTrue(e.getMessage().contains("not supported"), e.getMessage());
    }

    @Test
    void identifiersThatMerelyStartWithDelimiterAreFine() {
        assertEquals(List.of("delimiter_settings is ok"), split("delimiter_settings is ok"));
    }
}
