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
package com.wavemaker.runtime.data.util;

import java.util.Optional;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.BasicType;
import org.hibernate.type.spi.TypeConfiguration;

public class HibernateUtils {

    private HibernateUtils() {
    }

    public static TypeConfiguration getTypeConfiguration(Session session) {
        return ((SessionFactoryImplementor) session.getSessionFactory()).getTypeConfiguration();
    }

    /**
     * Finds the basic type registered with the given name, otherwise the basic type of the java class with the given name.
     */
    public static Optional<BasicType<?>> findType(TypeConfiguration typeConfiguration, String classOrType) {
        BasicType<?> type = typeConfiguration.getBasicTypeRegistry().getRegisteredType(classOrType);
        if (type == null) {
            try {
                type = typeConfiguration.getBasicTypeForJavaType(Class.forName(classOrType));
            } catch (ClassNotFoundException e) {
                // not a java class, no type found
            }
        }
        return Optional.ofNullable(type);
    }
}
