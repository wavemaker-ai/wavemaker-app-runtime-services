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
 * hibernate7: the conversions 5.6's BooleanTypeDescriptor did (Y / N for BOOLEAN and YES_OR_NO, T / F for TRUE_OR_FALSE).
 * 7.x's BooleanJavaType rejects strings / characters other than its own true / false values instead of reading them as
 * false, and has no T / F variant.
 */
public class BooleanTypeConverter implements JavaTypeConverter {

    private final char characterValueTrue;
    private final char characterValueFalse;
    private final char characterValueTrueLowerCase;
    private final String stringValueTrue;
    private final String stringValueFalse;

    public BooleanTypeConverter(char characterValueTrue, char characterValueFalse) {
        this.characterValueTrue = Character.toUpperCase(characterValueTrue);
        this.characterValueFalse = Character.toUpperCase(characterValueFalse);
        this.characterValueTrueLowerCase = Character.toLowerCase(characterValueTrue);
        this.stringValueTrue = String.valueOf(characterValueTrue);
        this.stringValueFalse = String.valueOf(characterValueFalse);
    }

    @Override
    public Object fromString(final String value) {
        return Boolean.valueOf(value);
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof Character character) {
            return isTrue(character);
        }
        if (value instanceof String string) {
            return !string.isEmpty() && isTrue(string.charAt(0));
        }
        throw new HibernateException("Unknown wrap conversion requested: " + value.getClass().getName() + " to " + Boolean.class.getName());
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        if (value == null) {
            return null;
        }
        final boolean booleanValue = (Boolean) value;
        if (Boolean.class.isAssignableFrom(toType)) {
            return value;
        }
        if (Byte.class.isAssignableFrom(toType)) {
            return (byte) (booleanValue ? 1 : 0);
        }
        if (Short.class.isAssignableFrom(toType)) {
            return (short) (booleanValue ? 1 : 0);
        }
        if (Integer.class.isAssignableFrom(toType) || Long.class.isAssignableFrom(toType)) {
            return booleanValue ? 1 : 0; // an Integer for Long too, as in 5.6
        }
        if (Character.class.isAssignableFrom(toType)) {
            return booleanValue ? characterValueTrue : characterValueFalse;
        }
        if (String.class.isAssignableFrom(toType)) {
            return booleanValue ? stringValueTrue : stringValueFalse;
        }
        throw new HibernateException("Unknown unwrap conversion requested: " + Boolean.class.getName() + " to " + toType.getName());
    }

    private boolean isTrue(char value) {
        return value == characterValueTrue || value == characterValueTrueLowerCase;
    }
}
