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

import org.hibernate.type.BigDecimalType;
import org.hibernate.type.BigIntegerType;
import org.hibernate.type.BooleanType;
import org.hibernate.type.ByteType;
import org.hibernate.type.CharacterType;
import org.hibernate.type.DoubleType;
import org.hibernate.type.FloatType;
import org.hibernate.type.IntegerType;
import org.hibernate.type.LongType;
import org.hibernate.type.ShortType;
import org.hibernate.type.TrueFalseType;
import org.hibernate.type.YesNoType;

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
            case BYTE -> new HibernateBackedJavaTypeConverter(ByteType.INSTANCE.getJavaTypeDescriptor());
            case SHORT -> new HibernateBackedJavaTypeConverter(ShortType.INSTANCE.getJavaTypeDescriptor());
            case INTEGER -> new HibernateBackedJavaTypeConverter(IntegerType.INSTANCE.getJavaTypeDescriptor());
            case LONG -> new HibernateBackedJavaTypeConverter(LongType.INSTANCE.getJavaTypeDescriptor());
            case BIG_INTEGER -> new HibernateBackedJavaTypeConverter(BigIntegerType.INSTANCE.getJavaTypeDescriptor());
            case FLOAT -> new HibernateBackedJavaTypeConverter(FloatType.INSTANCE.getJavaTypeDescriptor());
            case DOUBLE -> new HibernateBackedJavaTypeConverter(DoubleType.INSTANCE.getJavaTypeDescriptor());
            case BIG_DECIMAL -> new HibernateBackedJavaTypeConverter(BigDecimalType.INSTANCE.getJavaTypeDescriptor());
            case BOOLEAN -> new HibernateBackedJavaTypeConverter(BooleanType.INSTANCE.getJavaTypeDescriptor());
            case YES_OR_NO -> new HibernateBackedJavaTypeConverter(YesNoType.INSTANCE.getJavaTypeDescriptor());
            case TRUE_OR_FALSE -> new HibernateBackedJavaTypeConverter(TrueFalseType.INSTANCE.getJavaTypeDescriptor());
            case CHARACTER -> new HibernateBackedJavaTypeConverter(CharacterType.INSTANCE.getJavaTypeDescriptor());
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
