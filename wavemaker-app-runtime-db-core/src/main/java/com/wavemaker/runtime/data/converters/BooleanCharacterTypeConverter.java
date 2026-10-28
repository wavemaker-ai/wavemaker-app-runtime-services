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

/**
 * Converts a boolean with the rules of the Hibernate 5 types BooleanType, YesNoType and TrueFalseType, which Hibernate 6
 * removed. A character or a text from the database is true when its first character is the true character of the type,
 * in upper or lower case, and a number is true when it is not zero. The text of the UI or a filter is true only for
 * "true".
 */
public class BooleanCharacterTypeConverter implements JavaTypeConverter {

    private final char trueCharacter;

    private final char falseCharacter;

    public BooleanCharacterTypeConverter(final char trueCharacter, final char falseCharacter) {
        this.trueCharacter = trueCharacter;
        this.falseCharacter = falseCharacter;
    }

    @Override
    public Object fromString(final String value) {
        return value == null ? null : Boolean.valueOf(value);
    }

    @Override
    public Object fromDbValue(final Object value) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof Character character) {
            return isTrue(character);
        }
        if (value instanceof String text) {
            return !text.isEmpty() && isTrue(text.charAt(0));
        }
        throw new IllegalArgumentException("Cannot convert value '" + value + "' of " + value.getClass().getName() + " to Boolean");
    }

    @Override
    public Object toDbValue(final Object value, final Class<?> toType) {
        if (!(value instanceof Boolean bool)) {
            return value;
        }
        if (Character.class.equals(toType) || char.class.equals(toType)) {
            return bool ? trueCharacter : falseCharacter;
        }
        if (String.class.equals(toType)) {
            return String.valueOf(bool ? trueCharacter : falseCharacter);
        }
        if (Byte.class.equals(toType) || byte.class.equals(toType)) {
            return (byte) (bool ? 1 : 0);
        }
        if (Short.class.equals(toType) || short.class.equals(toType)) {
            return (short) (bool ? 1 : 0);
        }
        if (Integer.class.equals(toType) || int.class.equals(toType)) {
            return bool ? 1 : 0;
        }
        if (Long.class.equals(toType) || long.class.equals(toType)) {
            return bool ? 1L : 0L;
        }
        return bool;
    }

    private boolean isTrue(final char character) {
        return Character.toUpperCase(character) == Character.toUpperCase(trueCharacter);
    }
}
