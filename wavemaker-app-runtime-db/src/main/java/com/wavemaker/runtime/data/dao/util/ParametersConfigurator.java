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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.query.Query;
import org.hibernate.type.BasicType;
import org.hibernate.type.Type;

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
            final Object value = getValue(parameters, parameterName);
            final Optional<Type> typeOptional = resolver.resolveType(parameterName);

            boolean listType = Collection.class.isInstance(value);

            // hibernate7: setParameter / setParameterList(.., org.hibernate.type.Type) were removed in 6.0; typed binding now
            // takes a jakarta.persistence.metamodel.Type, which Hibernate's BasicType implements.
            // Every resolver (RuntimeParameterTypeResolver, HibernateUtils.findType) returns a registered BasicType or
            // nothing, so the untyped branch is only taken where 5.6 also bound the value untyped.
            if (typeOptional.isPresent() && typeOptional.get() instanceof BasicType<?> basicType) {
                if (listType) {
                    query.setParameterList(parameterName, (Collection) value, (BasicType) basicType);
                } else {
                    query.setParameter(parameterName, value, (BasicType) basicType);
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
