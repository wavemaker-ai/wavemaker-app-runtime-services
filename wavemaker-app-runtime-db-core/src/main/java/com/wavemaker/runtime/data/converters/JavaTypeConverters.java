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

import java.util.EnumMap;
import java.util.Map;

import org.hibernate.type.descriptor.java.BigDecimalJavaType;
import org.hibernate.type.descriptor.java.BigIntegerJavaType;
import org.hibernate.type.descriptor.java.ByteJavaType;
import org.hibernate.type.descriptor.java.CharacterJavaType;
import org.hibernate.type.descriptor.java.DoubleJavaType;
import org.hibernate.type.descriptor.java.FloatJavaType;
import org.hibernate.type.descriptor.java.IntegerJavaType;
import org.hibernate.type.descriptor.java.LongJavaType;
import org.hibernate.type.descriptor.java.ShortJavaType;

import com.wavemaker.runtime.data.model.JavaType;

/**
 * Holds the {@link JavaType} to {@link JavaTypeConverter} mapping, keeping the converter
 * (Hibernate-backed) wiring separate from the {@link JavaType} enum definition itself.
 * <p>
 * {@link #createConverter} is a switch expression with no {@code default} branch, so adding a new
 * {@link JavaType} constant without adding a corresponding case here fails the build at compile time
 * ("the switch expression does not cover all possible input values").
 */
public final class JavaTypeConverters {

    private static final Map<JavaType, JavaTypeConverter> CONVERTERS = buildConverters();

    private JavaTypeConverters() {
    }

    public static JavaTypeConverter get(final JavaType javaType) {
        return CONVERTERS.get(javaType);
    }

    private static Map<JavaType, JavaTypeConverter> buildConverters() {
        final Map<JavaType, JavaTypeConverter> converters = new EnumMap<>(JavaType.class);
        for (final JavaType javaType : JavaType.values()) {
            converters.put(javaType, createConverter(javaType));
        }
        return converters;
    }

    private static JavaTypeConverter createConverter(final JavaType javaType) {
        return switch (javaType) {
            case BYTE -> new HibernateBackedJavaTypeConverter(ByteJavaType.INSTANCE);
            case SHORT -> new HibernateBackedJavaTypeConverter(ShortJavaType.INSTANCE);
            case INTEGER -> new HibernateBackedJavaTypeConverter(IntegerJavaType.INSTANCE);
            case LONG -> new HibernateBackedJavaTypeConverter(LongJavaType.INSTANCE);
            case BIG_INTEGER -> new HibernateBackedJavaTypeConverter(BigIntegerJavaType.INSTANCE);
            case FLOAT -> new HibernateBackedJavaTypeConverter(FloatJavaType.INSTANCE);
            case DOUBLE -> new HibernateBackedJavaTypeConverter(DoubleJavaType.INSTANCE);
            case BIG_DECIMAL -> new HibernateBackedJavaTypeConverter(BigDecimalJavaType.INSTANCE);
            case BOOLEAN -> new BooleanCharacterTypeConverter('Y', 'N');
            case YES_OR_NO -> new BooleanCharacterTypeConverter('Y', 'N');
            case TRUE_OR_FALSE -> new BooleanCharacterTypeConverter('T', 'F');
            case CHARACTER -> new HibernateBackedJavaTypeConverter(CharacterJavaType.INSTANCE);
            case STRING, TEXT, CLOB -> new StringTypeConverter();
            case BLOB -> new BlobTypeConverter();
            case DATE -> new DateTypeConverter();
            case TIME -> new TimeTypeConverter();
            case DATETIME -> new DateTimeConverter();
            case TIMESTAMP -> new TimestampTypeConverter();
            case CURSOR -> new ObjectTypeConverter();
        };
    }
}
