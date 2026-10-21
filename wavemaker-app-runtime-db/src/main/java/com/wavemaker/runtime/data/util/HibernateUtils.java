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

import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.type.Type;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 22/8/17
 */
public class HibernateUtils {

    // hibernate7: TypeHelper (basic / heuristicType) was removed in 6.0; basic types are looked up by name or class name
    // in the BasicTypeRegistry.
    // heuristicType also fell back to Serializable / custom-type resolution for unregistered names; callers only pass
    // JavaType class names, all of which are registered in 5.6 and 7.x alike (checked), so that fallback is never used.
    public static Optional<Type> findType(TypeConfiguration typeConfiguration, String classOrType) {
        return Optional.ofNullable(typeConfiguration.getBasicTypeRegistry().getRegisteredType(classOrType));
    }
}
