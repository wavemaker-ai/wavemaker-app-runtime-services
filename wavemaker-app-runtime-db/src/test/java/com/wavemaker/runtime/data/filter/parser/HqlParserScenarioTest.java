/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.filter.parser;

import java.util.StringJoiner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wavemaker.runtime.data.exception.HqlGrammarException;
import com.wavemaker.runtime.data.filter.WMQueryGrammarInterceptor;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.filter.parser.utils.models.Model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "WMQL Filter Parser" section: {@link HqlParser} and {@link WMQueryGrammarInterceptor}, which turn a WMQL filter
 * into an HQL condition with every value bound as a parameter. Each test checks the exact output; the
 * data-provider-driven {@link HqlParserTest} runs the parser across every field type.
 *
 * <p>The spec's sample output uses an entity alias and named placeholders ({@code e.name = :wmql_name_0}). The real
 * output has no alias and numbered placeholders: {@code wmString = :wm_parsed_param0}. Property paths are resolved
 * by plain reflection on the entity class, with no Hibernate metadata involved.
 */
class HqlParserScenarioTest {

    private static final HqlFilterPropertyResolver MODEL = new HqlFilterPropertyResolverImpl(Model.class);

    @Test
    void equalityBindsTheValueAsAParameter() {
        assertEquals("wmString = :wm_parsed_param0 {wm_parsed_param0=John STRING}", parsed("wmString = 'John'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"=", "!=", "<>", "<", ">", "<=", ">="})
    void eachComparisonOperatorIsKeptAndTheValueIsTypedFromTheField(String operator) {
        assertEquals("wmInteger " + operator + " :wm_parsed_param0 {wm_parsed_param0=10 INTEGER}",
            parsed("wmInteger " + operator + " 10"));
        assertEquals("wmDate " + operator + " :wm_parsed_param0 {wm_parsed_param0=2020-01-01 DATE}",
            parsed("wmDate " + operator + " '2020-01-01'"));
        assertEquals("wmString " + operator + " :wm_parsed_param0 {wm_parsed_param0=John STRING}",
            parsed("wmString " + operator + " 'John'"));
    }

    @Test
    void andOrKeepsTheWrittenOrderAndBracketsLeavingPrecedenceToHql() {
        // The parser adds no brackets of its own: AND binding tighter than OR is HQL's precedence, and brackets the
        // user wrote are kept.
        assertEquals("wmInteger = :wm_parsed_param0 and wmLong = :wm_parsed_param1 or wmBoolean = :wm_parsed_param2"
                + " {wm_parsed_param0=1 INTEGER, wm_parsed_param1=2 LONG, wm_parsed_param2=true BOOLEAN}",
            parsed("wmInteger = 1 and wmLong = 2 or wmBoolean = true"));
        assertEquals("wmInteger = :wm_parsed_param0 and ( wmLong = :wm_parsed_param1 or wmBoolean = :wm_parsed_param2 )"
                + " {wm_parsed_param0=1 INTEGER, wm_parsed_param1=2 LONG, wm_parsed_param2=true BOOLEAN}",
            parsed("wmInteger = 1 and (wmLong = 2 or wmBoolean = true)"));
    }

    @Test
    void notExistsOnlyAsNotInAndNotLikeNotAsAPrefixOperator() {
        // WMQL has no "NOT <condition>" (the spec's NOT status = 'DELETED'); NOT is part of NOT IN, NOT LIKE and
        // IS NOT NULL only.
        assertEquals("wmInteger not in ( :wm_parsed_param0 , :wm_parsed_param1 ) {wm_parsed_param0=1 INTEGER, wm_parsed_param1=2 INTEGER}",
            parsed("wmInteger not in (1, 2)"));
        assertEquals("wmString not like :wm_parsed_param0 {wm_parsed_param0=John% STRING}",
            parsed("wmString not like 'John%'"));

        HqlGrammarException exception = assertThrows(HqlGrammarException.class, () -> parse("not wmBoolean = true"));
        assertEquals(0, exception.getMessageResourceHolder().getArgs()[1], "the error is at column 0");
    }

    @Test
    void inBindsEachListValueSeparately() {
        assertEquals("wmInteger in ( :wm_parsed_param0 , :wm_parsed_param1 , :wm_parsed_param2 )"
                + " {wm_parsed_param0=1 INTEGER, wm_parsed_param1=2 INTEGER, wm_parsed_param2=3 INTEGER}",
            parsed("wmInteger in (1,2,3)"));
    }

    @Test
    void betweenBindsBothBoundsWithTheFieldsType() {
        assertEquals("wmInteger between :wm_parsed_param0 and :wm_parsed_param1 {wm_parsed_param0=18 INTEGER, wm_parsed_param1=65 INTEGER}",
            parsed("wmInteger between 18 and 65"));
        assertEquals("wmDate between :wm_parsed_param0 and :wm_parsed_param1"
                + " {wm_parsed_param0=2020-01-01 DATE, wm_parsed_param1=2020-12-31 DATE}",
            parsed("wmDate between '2020-01-01' and '2020-12-31'"));
    }

    @Test
    void likeBindsThePatternWithItsWildcardsUnescaped() {
        assertEquals("wmString like :wm_parsed_param0 {wm_parsed_param0=J_hn% STRING}", parsed("wmString like 'J_hn%'"));
    }

    @Test
    void nullChecksBindNoParameter() {
        assertEquals("wmString is null", parsed("wmString is null"));
        assertEquals("wmString is not null", parsed("wmString is not null"));
    }

    @Test
    void comparingWithNullKeepsTheNullLiteralSoTheConditionMatchesNoRow() {
        // "= null" is never true in SQL; "is null" is the form that matches null values.
        assertEquals("wmString = null", parsed("wmString = null"));
    }

    @Test
    void nestedPropertyPathsAreResolvedOneLevelAtATime() {
        assertEquals("child.wmString = :wm_parsed_param0 {wm_parsed_param0=NY STRING}", parsed("child.wmString = 'NY'"));
        assertEquals("child.grandChild.wmInteger = :wm_parsed_param0 {wm_parsed_param0=5 INTEGER}",
            parsed("child.grandChild.wmInteger = 5"));
    }

    @Test
    void anInvalidPropertyInTheMiddleOfAPathIsRejected() {
        HqlGrammarException exception = assertThrows(HqlGrammarException.class, () -> parse("child.nope.wmString = 'NY'"));

        assertArrayEquals(new Object[]{"child.nope.wmString", Model.class.getName()},
            exception.getMessageResourceHolder().getArgs());
    }

    @Test
    void aSyntaxErrorReportsTheColumnAndTheParsersMessage() {
        HqlGrammarException exception = assertThrows(HqlGrammarException.class, () -> parse("wmString ="));

        // The args are the query, the column and ANTLR's message; the line is not captured. The message itself is
        // only the key "com.wavemaker.runtime.data$SyntaxErrorInQuery", which has no text.
        Object[] args = exception.getMessageResourceHolder().getArgs();
        assertEquals("wmString =", args[0]);
        assertEquals(10, args[1]);
        assertTrue(((String) args[2]).startsWith("mismatched input '<EOF>'"), () -> String.valueOf(args[2]));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wmString='' or true=true", "wmString='asdf' or 1=1", "wmString=105 OR 1=1",
        "wmString=2 or 1 is not null"})
    void injectionAttemptsAreRejectedBecauseTheGrammarCannotParseThem(String wmql) {
        assertThrows(HqlGrammarException.class, () -> parse(wmql));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wmString = 'x' #", "wmString=123; delete TABLE Employee;"})
    void aCharacterTheLexerDoesNotRecogniseCurrentlyThrowsClassCastException(String wmql) {
        // Bug: WMHqlAntlrErrorListner casts the recognizer's input to TokenStream, which is only true for the parser.
        // For a lexer error the input is a CharStream, so it should throw HqlGrammarException like a parser error.
        assertThrows(ClassCastException.class, () -> parse(wmql));
    }

    @Test
    void theInterceptorReplacesTheQueryUnescapesDoubledQuotesAndKeepsExistingParameters() {
        WMQueryInfo queryInfo = new WMQueryInfo("wmString = 'O''Brien'");
        queryInfo.addParameter("existing", "kept");

        new WMQueryGrammarInterceptor().intercept(queryInfo, Model.class);

        assertEquals("wmString = :wm_parsed_param0", queryInfo.getQuery());
        assertEquals("O'Brien", queryInfo.getParameters().get("wm_parsed_param0").getValue());
        assertEquals("kept", queryInfo.getParameters().get("existing").getValue());
    }

    @Test
    void aBackslashEscapedQuoteIsCurrentlyBoundWithTheBackslash() {
        // Bug: the grammar accepts \' as an escaped quote like '', but the interceptor only unescapes ''.
        WMQueryInfo queryInfo = new WMQueryInfo("wmString = 'a\\'b'");

        new WMQueryGrammarInterceptor().intercept(queryInfo, Model.class);

        assertEquals("a\\'b", queryInfo.getParameters().get("wm_parsed_param0").getValue());
    }

    private static WMQueryInfo parse(String wmql) {
        return HqlParser.getInstance().parse(wmql, MODEL);
    }

    /**
     * The query followed by its parameters as {@code {name=value TYPE, ...}}, so one assertion covers both.
     */
    private static String parsed(String wmql) {
        WMQueryInfo info = parse(wmql);
        StringJoiner parameters = new StringJoiner(", ", " {", "}").setEmptyValue("");
        info.getParameters().forEach((name, param) -> parameters.add(name + "=" + param.getValue() + " " + param.getJavaType()));
        return info.getQuery() + parameters;
    }
}
