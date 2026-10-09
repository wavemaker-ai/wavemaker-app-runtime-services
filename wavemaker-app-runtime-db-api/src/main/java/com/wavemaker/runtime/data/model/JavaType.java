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
package com.wavemaker.runtime.data.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.wavemaker.commons.WMRuntimeException;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 11/2/16
 */
public enum JavaType {

    BYTE(byte.class.getName(), Byte.class.getName()),
    SHORT(short.class.getName(), Short.class.getName()),
    INTEGER(int.class.getName(), Integer.class.getName()),
    LONG(long.class.getName(), Long.class.getName()),
    BIG_INTEGER(BigInteger.class.getName()),
    FLOAT(float.class.getName(), Float.class.getName()),
    DOUBLE(double.class.getName(), Double.class.getName()),
    BIG_DECIMAL(BigDecimal.class.getName()), // OR NUMBER
    BOOLEAN(boolean.class.getName(), Boolean.class.getName()),
    YES_OR_NO(boolean.class.getName(), Boolean.class.getName()),
    TRUE_OR_FALSE(boolean.class.getName(), Boolean.class.getName()),
    CHARACTER(char.class.getName(), Character.class.getName()),
    STRING(String.class.getName()),
    TEXT(String.class.getName()),
    CLOB(String.class.getName()),
    BLOB("byte[]") {
        @Override
        public Class<?> getClassType() {
            return byte[].class;
        }
    },
    DATE(Date.class.getName()),
    TIME(Time.class.getName()),
    DATETIME(LocalDateTime.class.getName()),
    TIMESTAMP(Timestamp.class.getName()),
    CURSOR(Object.class.getName());
    //    CALENDAR(),
//    CALENDAR_DATE(),
//    CURRENCY(),
//    LOCALE(),
//    TIMEZONE(),

    private static Map<String, JavaType> javaTypeMap = new HashMap<>();

    private static List<JavaType> integerTypes = Arrays.asList(JavaType.SHORT,
        JavaType.INTEGER,
        JavaType.LONG,
        JavaType.BIG_INTEGER);

    private static List<JavaType> decimalTypes = Arrays.asList(JavaType.FLOAT,
        JavaType.DOUBLE,
        JavaType.BIG_DECIMAL);

    private static List<JavaType> numericTypes = new ArrayList<>();

    static {
        for (final JavaType javaType : JavaType.values()) {
            javaTypeMap.put(javaType.toValue(), javaType);
        }

        numericTypes.addAll(integerTypes);
        numericTypes.addAll(decimalTypes);
    }

    private final String className;
    private final String primitiveClassName;

    JavaType(final String primitiveClassName, final String wrapperClassName) {
        this.primitiveClassName = primitiveClassName;
        this.className = wrapperClassName;
    }

    JavaType(final String className) {
        this(className, className);
    }

    public String getClassName() {
        return this.className;
    }

    public Class<?> getClassType() {
        try {
            return Class.forName(getClassName());
        } catch (ClassNotFoundException e) {
            throw new WMRuntimeException(e);
        }
    }

    public String getPrimitiveClassName() {
        return primitiveClassName;
    }

    public static List<JavaType> integerTypes() {
        return integerTypes;
    }

    public static List<JavaType> decimalTypes() {
        return decimalTypes;
    }

    public static List<JavaType> numericTypes() {
        return numericTypes;
    }

    public boolean isNumericType() {
        return numericTypes().contains(this);
    }

    public boolean isDecimalType() {
        return decimalTypes().contains(this);
    }

    public boolean isIntegerType() {
        return integerTypes().contains(this);
    }

    @JsonCreator
    public static JavaType fromValue(String value) {
        return javaTypeMap.get(value);
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
