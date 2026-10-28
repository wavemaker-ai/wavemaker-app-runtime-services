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
package com.wavemaker.runtime.data.dao.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.query.Query;
import org.hibernate.query.QueryParameter;
import org.hibernate.type.BasicType;
import org.hibernate.type.BindableType;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.commons.variable.VariableTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.HqlParameterTypeResolver;
import com.wavemaker.runtime.data.dao.query.types.ParameterTypeResolver;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 21/7/17
 */
// TODO Redundant class, remove
public class ParametersConfigurator {

    private ParametersConfigurator() {
    }

    public static <R> Query<R> configure(Query<R> query, Map<String, Object> parameters) {
        return configure(query, parameters, new HqlParameterTypeResolver());
    }

    public static <R> Query<R> configure(
        Query<R> query, Map<String, Object> parameters, ParameterTypeResolver resolver) {
        query.getParameterMetadata().getNamedParameterNames().forEach(parameterName -> {
            final Object rawValue = getValue(parameters, parameterName);
            final Object value = coerce(query, parameterName, rawValue);
            // a converted value has the type the query expects, so the explicit type of the parameter is not used for it
            final Optional<BasicType<?>> typeOptional = value == rawValue ? resolver.resolveType(parameterName) : Optional.empty();

            boolean listType = Collection.class.isInstance(value);

            if (typeOptional.isPresent()) {
                if (listType) {
                    query.setParameterList(parameterName, (Collection) value, (BindableType) typeOptional.get());
                } else {
                    query.setParameter(parameterName, value, (BindableType) typeOptional.get());
                }
            } else {
                if (listType) {
                    query.setParameterList(parameterName, (Collection) value);
                } else {
                    query.setParameter(parameterName, value);
                }
            }
        });

        return query;
    }

    /**
     * Hibernate 5 converted a java.sql date, time or timestamp for the java.time attribute it was compared to. Hibernate 7
     * rejects it, so it is converted here to the java.time type the query expects for the parameter.
     */
    public static Object coerce(final Query<?> query, final String parameterName, final Object value) {
        if (value instanceof Collection<?> values) {
            final List<Object> converted = values.stream().map(element -> coerce(query, parameterName, element)).toList();
            return converted.equals(new ArrayList<>(values)) ? value : converted;
        }
        if (value == null || !(value instanceof java.util.Date)) {
            return value;
        }
        final QueryParameter<?> parameter = query.getParameterMetadata().getQueryParameter(parameterName);
        Class<?> expected = parameter.getParameterType();
        if (expected == null && parameter.getHibernateType() != null) {
            expected = parameter.getHibernateType().getJavaType();
        }
        if (expected == null || expected.isInstance(value)) {
            return value;
        }
        if (value instanceof java.sql.Timestamp timestamp && java.time.LocalDateTime.class.equals(expected)) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date && java.time.LocalDate.class.equals(expected)) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Time time && java.time.LocalTime.class.equals(expected)) {
            return time.toLocalTime();
        }
        return value;
    }

    private static Object getValue(final Map<String, Object> parameters, final String parameterName) {
        Object value = parameters.get(parameterName);
        // looking for system variables, only for null values.
        if (value == null) {
            final Pair<VariableType, String> variableInfo = VariableTypeHelper.fromVariableName(parameterName);
            VariableType variableType = variableInfo.getLeft();
            if (variableType.isVariable()) {
                String variableName = variableInfo.getRight();
                value = variableType.getValue(variableName);
            }
        }
        return value;
    }

}
