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

/**
 * hibernate7: the conversions 5.6's CharacterTypeDescriptor did. 7.x's CharacterJavaType rejects a string of more than
 * one character instead of taking its first one.
 */
public class CharacterTypeConverter implements JavaTypeConverter {

    @Override
    public Object fromString(final String value) {
        if (value.length() != 1) {
            throw new HibernateException("multiple or zero characters found parsing string");
        }
        return value.charAt(0);
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Character) {
            return value;
        }
        if (value instanceof String string) {
            return string.charAt(0);
        }
        if (value instanceof Number number) {
            return (char) number.shortValue();
        }
        throw new HibernateException("Unknown wrap conversion requested: " + value.getClass().getName() + " to " + Character.class.getName());
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        if (value == null) {
            return null;
        }
        final Character character = (Character) value;
        if (Character.class.isAssignableFrom(toType)) {
            return character;
        }
        if (String.class.isAssignableFrom(toType)) {
            return character.toString();
        }
        if (Number.class.isAssignableFrom(toType)) {
            return (short) character.charValue();
        }
        throw new HibernateException("Unknown unwrap conversion requested: " + Character.class.getName() + " to " + toType.getName());
    }
}
