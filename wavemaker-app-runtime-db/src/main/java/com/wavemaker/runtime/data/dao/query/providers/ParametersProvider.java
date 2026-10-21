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
package com.wavemaker.runtime.data.dao.query.providers;

import java.util.Collection;
import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.type.BasicType;
import org.hibernate.type.Type;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 4/8/17
 */
public interface ParametersProvider {

    /**
     * Returns the value for given parameter name.
     *
     * @param name parameter name.
     *
     * @return parameter value, null if no value present.
     */
    Object getValue(final Session session, String name);

    /**
     * Returns the {@link Class#getCanonicalName()} for given parameter name.
     *
     * @param name parameter name.
     *
     * @return returns class name, {@link Optional#empty()} if not present.
     */
    Optional<Type> getType(Session session, String name);

    /**
     * Utility method to configure parameters to given query.
     *
     * @param session hibernate session
     * @param query   query to be configured
     * @param <R>     query return type.
     *
     * @return Returns the given query
     */
    default <R> Query<R> configure(Session session, Query<R> query) {
        query.getParameterMetadata().getNamedParameterNames().forEach(parameterName -> {
            final Object value = getValue(session, parameterName);
            final Optional<Type> typeOptional = getType(session, parameterName);

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

}
