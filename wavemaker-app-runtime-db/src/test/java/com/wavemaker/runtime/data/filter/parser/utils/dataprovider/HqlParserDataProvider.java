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
package com.wavemaker.runtime.data.filter.parser.utils.dataprovider;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.BetweenQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.DataTypeQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.InQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.InvalidPropertyQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.LikeQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.NestedBracesQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.NullCheckQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.PropertyQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.SqlInjectionQueries;
import com.wavemaker.runtime.data.filter.parser.utils.dataprovider.queries.SyntaxErrorQueries;

/**
 * @author Sujith Simon
 * Created on : 2/11/18
 */
public class HqlParserDataProvider {

    public static Stream<Arguments> dataTypeQueriesProvider() {
        return getParameters(DataTypeQueries::getQueries, FieldsMetadata.getFields());
    }

    public static Stream<Arguments> nullValuesQueriesProvider() {
        return getParameters(NullCheckQueries::getQueries, FieldsMetadata.getFields());
    }

    public static Stream<Arguments> syntaxErrorQueriesProvider() {
        return getParameters(SyntaxErrorQueries::getQueries, Collections.singletonList(String.class));
    }

    public static Stream<Arguments> sqlInjectionQueriesProvider() {
        return getParameters(SqlInjectionQueries::getQueries, Collections.singletonList(String.class));
    }

    public static Stream<Arguments> betweenPositiveQueriesProvider() {
        return getParameters(BetweenQueries::getPositiveQueries, FieldsMetadata.getFieldsExcluding(Boolean.class, String.class));
    }

    public static Stream<Arguments> betweenNegativeQueriesProvider() {
        return getParameters(BetweenQueries::getNegativeQueries, FieldsMetadata.getFieldsExcluding(Boolean.class, String.class));
    }

    public static Stream<Arguments> inPositiveQueriesProvider() {
        return getParameters(InQueries::getPositiveQueries, FieldsMetadata.getFieldsExcluding(Boolean.class, String.class));
    }

    public static Stream<Arguments> inNegativeQueriesProvider() {
        return getParameters(InQueries::getNegativeQueries, FieldsMetadata.getFieldsExcluding(Boolean.class, String.class));
    }

    public static Stream<Arguments> likePositiveQueriesProvider() {
        return getParameters(LikeQueries::getPositiveQueries, Collections.singletonList(String.class));
    }

    public static Stream<Arguments> likeNegativeQueriesProvider() {
        return getParameters(LikeQueries::getNegativeQueries, Collections.singletonList(String.class));
    }

    public static Stream<Arguments> nestedBracesPositiveQueriesProvider() {
        return getParameters(NestedBracesQueries::getPositiveQueries, FieldsMetadata.getFields());
    }

    public static Stream<Arguments> nestedBracesNegativeQueriesProvider() {
        return getParameters(NestedBracesQueries::getNegativeQueries, FieldsMetadata.getFields());
    }

    public static Stream<Arguments> propertyPositiveQueriesProvider() {
        return getParameters(PropertyQueries::getPositiveQueries, Collections.singletonList(String.class));
    }

    public static Stream<Arguments> propertyNegativeQueriesProvider() {
        return getParameters(PropertyQueries::getNegativeQueries, Collections.singletonList(String.class));
    }

    public static Stream<Arguments> InvalidPropertyQueriesProvider() {
        return getParameters(InvalidPropertyQueries::getQueries, Collections.singletonList(String.class));
    }

    private static Stream<Arguments> getParameters(QueriesProvider queriesProvider, List<Class<?>> fields) {
        return fields.stream().map(dataType -> Arguments.of(dataType, queriesProvider.getQueries(dataType)));
    }

    @FunctionalInterface
    private interface QueriesProvider {
        List<String> getQueries(Class dataType);
    }

}
