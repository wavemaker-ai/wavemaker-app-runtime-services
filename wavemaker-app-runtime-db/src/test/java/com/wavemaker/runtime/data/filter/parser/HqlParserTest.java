/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wavemaker.runtime.data.filter.parser;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wavemaker.runtime.data.exception.HqlGrammarException;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.filter.WMQueryParamInfo;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider;
import com.wavemaker.runtime.data.filter.parser.utils.models.Model;

/**
 * @author Sujith Simon
 * Created on : 26/10/18
 */
public class HqlParserTest extends HqlParserDataProvider {

    private Logger logger = LoggerFactory.getLogger(HqlParserTest.class);

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#dataTypeQueriesProvider")
    public void comparisionAndDataTypeCheck(Class dateType, List<String> queries) throws ClassNotFoundException {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        assert queries != null;

        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            for (WMQueryParamInfo wmQueryParamInfo : wmQueryInfo.getParameters().values()) {
                Assertions.assertSame(dateType, Class.forName(wmQueryParamInfo.getJavaType().getClassName()),
                    "'" + wmQueryParamInfo + "' in '" + query + "' could not be converted to " + dateType);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#nullValuesQueriesProvider")
    public void nullValues(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        assert queries != null;

        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            Assertions.assertEquals(0, wmQueryInfo.getParameters().size());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#syntaxErrorQueriesProvider")
    public void syntaxErrors(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        assert queries != null;

        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @Disabled("WMHqlAntlrErrorListner throws ClassCastException (ANTLRInputStream cannot be cast to TokenStream) — pre-existing production bug")
    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#sqlInjectionQueriesProvider")
    public void sqlInjections(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        assert queries != null;

        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#betweenPositiveQueriesProvider")
    public void betweenPositive(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            Assertions.assertNotNull(wmQueryInfo);
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#betweenNegativeQueriesProvider")
    public void betweenNegative(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#inPositiveQueriesProvider")
    public void inPositive(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            Assertions.assertNotNull(wmQueryInfo);
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#inNegativeQueriesProvider")
    public void inNegative(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#likePositiveQueriesProvider")
    public void likePositive(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            Assertions.assertNotNull(wmQueryInfo);
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#likeNegativeQueriesProvider")
    public void likeNegative(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#nestedBracesPositiveQueriesProvider")
    public void nestedBracesPositive(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            Assertions.assertNotNull(wmQueryInfo);
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#nestedBracesNegativeQueriesProvider")
    public void nestedBracesNegative(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#propertyPositiveQueriesProvider")
    public void propertyPositive(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            WMQueryInfo wmQueryInfo = HqlParser.getInstance().parse(query, propertyResolver);
            Assertions.assertNotNull(wmQueryInfo);
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#propertyNegativeQueriesProvider")
    public void propertyNegative(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("com.wavemaker.runtime.data.filter.parser.utils.dataprovider.HqlParserDataProvider#InvalidPropertyQueriesProvider")
    public void invalidProperties(Class dateType, List<String> queries) {
        logger.debug("Testing for the Data type {}.", dateType);
        HqlFilterPropertyResolver propertyResolver = new HqlFilterPropertyResolverImpl(Model.class);
        for (String query : queries) {
            HqlGrammarException ex = Assertions.assertThrows(HqlGrammarException.class,
                () -> HqlParser.getInstance().parse(query, propertyResolver));
            Assertions.assertNotNull(ex.getMessage());
        }
    }

}
