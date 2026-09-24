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

import java.util.function.Supplier;

import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

import com.wavemaker.app.db.converters.JavaTypeConverter;

/**
 * Converts values using a Hibernate type, loaded only when actually needed.
 * <p>
 * The Hibernate type is passed in as a {@link Supplier}, not resolved up front. This means creating a
 * converter does not touch Hibernate at all - Hibernate is only loaded the first time a value is actually
 * converted.
 *
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 12/1/17
 */
public class HibernateBackedJavaTypeConverter implements JavaTypeConverter {

    private final Supplier<JavaTypeDescriptor> descriptorSupplier;
    private JavaTypeDescriptor descriptor;

    public HibernateBackedJavaTypeConverter(final Supplier<JavaTypeDescriptor> descriptorSupplier) {
        this.descriptorSupplier = descriptorSupplier;
    }

    private JavaTypeDescriptor descriptor() {
        if (descriptor == null) {
            descriptor = descriptorSupplier.get();
        }
        return descriptor;
    }

    @Override
    public Object fromString(final String value) {
        return descriptor().fromString(value);
    }

    @Override
    public Object fromDbValue(final Object value) {
        return descriptor().wrap(value, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object toDbValue(final Object value, Class<?> toType) {
        return descriptor().unwrap(value, toType, null);
    }
}
