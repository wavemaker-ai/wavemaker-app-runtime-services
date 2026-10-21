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

// hibernate7: JavaTypeDescriptor was renamed org.hibernate.type.descriptor.java.JavaType in 6.0 (same fromString/wrap/unwrap)
import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 12/1/17
 */
public class HibernateBackedJavaTypeConverter implements JavaTypeConverter {

    @SuppressWarnings("rawtypes")
    private JavaType descriptor;

    public HibernateBackedJavaTypeConverter(final JavaType<?> descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public Object fromString(final String value) {
        return descriptor.fromString(value);
    }

    // hibernate7: wrap / unwrap are called with null WrapperOptions as in 5.6. For the descriptors still used here (byte to
    // double, big decimal / integer, string, blob) the 7.x results were compared with 5.6's on the same inputs; the
    // differences are handled by the subclasses / the WM converters JavaTypeConverters picks instead.
    @SuppressWarnings("unchecked")
    @Override
    public Object fromDbValue(final Object value) {
        return descriptor.wrap(value, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object toDbValue(final Object value, Class<?> toType) {
        return descriptor.unwrap(value, toType, null);
    }
}
