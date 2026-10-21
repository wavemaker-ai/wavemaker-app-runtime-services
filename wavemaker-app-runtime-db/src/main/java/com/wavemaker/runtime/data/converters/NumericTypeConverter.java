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
package com.wavemaker.runtime.data.converters;

import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * hibernate7: 7.x's BigDecimalJavaType / BigIntegerJavaType also wrap strings (parsing them, or failing with a
 * NumberFormatException); 5.6 rejected them with a HibernateException, which callers such as JavaTypeUtils.convert catch.
 */
public class NumericTypeConverter extends HibernateBackedJavaTypeConverter {

    private final Class<?> type;

    public NumericTypeConverter(final JavaType<?> descriptor) {
        super(descriptor);
        this.type = descriptor.getJavaTypeClass();
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value instanceof String) {
            throw new HibernateException("Unknown wrap conversion requested: " + String.class.getName() + " to " + type.getName());
        }
        return super.fromDbValue(value);
    }
}
