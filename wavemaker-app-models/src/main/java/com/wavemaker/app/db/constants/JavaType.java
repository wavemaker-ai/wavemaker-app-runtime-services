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
package com.wavemaker.app.db.constants;

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
import com.wavemaker.app.db.converters.DateTimeConverter;
import com.wavemaker.app.db.converters.DefaultBlobConverter;
import com.wavemaker.app.db.converters.DefaultBooleanConverter;
import com.wavemaker.app.db.converters.DefaultCharacterConverter;
import com.wavemaker.app.db.converters.DefaultDateConverter;
import com.wavemaker.app.db.converters.DefaultNumberConverter;
import com.wavemaker.app.db.converters.DefaultStringConverter;
import com.wavemaker.app.db.converters.DefaultTimeConverter;
import com.wavemaker.app.db.converters.DefaultTimestampConverter;
import com.wavemaker.app.db.converters.JavaTypeConverter;
import com.wavemaker.app.db.converters.ObjectTypeConverter;
import com.wavemaker.commons.WMRuntimeException;

/**
 * @author <a href="mailto:dilip.gundu@wavemaker.com">Dilip Kumar</a>
 * @since 11/2/16
 */
public enum JavaType {

    BYTE(byte.class.getName(), Byte.class.getName(), new DefaultNumberConverter(Byte.class, Byte::valueOf, Number::byteValue)),
    SHORT(short.class.getName(), Short.class.getName(), new DefaultNumberConverter(Short.class, Short::valueOf, Number::shortValue)),
    INTEGER(int.class.getName(), Integer.class.getName(), new DefaultNumberConverter(Integer.class, Integer::valueOf, Number::intValue)),
    LONG(long.class.getName(), Long.class.getName(), new DefaultNumberConverter(Long.class, Long::valueOf, Number::longValue)),
    BIG_INTEGER(BigInteger.class.getName(), new DefaultNumberConverter(BigInteger.class, BigInteger::new, number -> BigInteger.valueOf(number.longValue()))),
    FLOAT(float.class.getName(), Float.class.getName(), new DefaultNumberConverter(Float.class, Float::valueOf, Number::floatValue)),
    DOUBLE(double.class.getName(), Double.class.getName(), new DefaultNumberConverter(Double.class, Double::valueOf, Number::doubleValue)),
    BIG_DECIMAL(BigDecimal.class.getName(), new DefaultNumberConverter(BigDecimal.class, BigDecimal::new, number -> new BigDecimal(number.toString()))), // OR NUMBER
    BOOLEAN(boolean.class.getName(), Boolean.class.getName(), new DefaultBooleanConverter()),
    YES_OR_NO(boolean.class.getName(), Boolean.class.getName(), new DefaultBooleanConverter()),
    TRUE_OR_FALSE(boolean.class.getName(), Boolean.class.getName(), new DefaultBooleanConverter()),
    CHARACTER(char.class.getName(), Character.class.getName(), new DefaultCharacterConverter()),
    STRING(String.class.getName(), new DefaultStringConverter()),
    TEXT(String.class.getName(), new DefaultStringConverter()),
    CLOB(String.class.getName(), new DefaultStringConverter()),
    BLOB("byte[]", new DefaultBlobConverter()) {
        @Override
        public Class<?> getClassType() {
            return byte[].class;
        }
    },
    DATE(Date.class.getName(), new DefaultDateConverter()),
    TIME(Time.class.getName(), new DefaultTimeConverter()),
    DATETIME(LocalDateTime.class.getName(), new DateTimeConverter()),
    TIMESTAMP(Timestamp.class.getName(), new DefaultTimestampConverter()),
    CURSOR(Object.class.getName(), new ObjectTypeConverter());
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

    // Works everywhere, with no database. wavemaker-app-runtime-db replaces this with a Hibernate-backed
    // converter (see dbConverter below) when it's actually on the classpath, for accurate database conversion.
    private final JavaTypeConverter defaultConverter;
    private volatile JavaTypeConverter dbConverter;

    JavaType(final String primitiveClassName, final String wrapperClassName, final JavaTypeConverter defaultConverter) {
        this.primitiveClassName = primitiveClassName;
        this.className = wrapperClassName;
        this.defaultConverter = defaultConverter;
    }

    JavaType(final String className, final JavaTypeConverter defaultConverter) {
        this(className, className, defaultConverter);
    }

    /**
     * Called by wavemaker-app-runtime-db at startup, only when it's present, to switch this type over to
     * real database conversion. If it's never called, the default converter keeps handling everything.
     */
    public void registerDbConverter(final JavaTypeConverter converter) {
        this.dbConverter = converter;
    }

    private JavaTypeConverter converter() {
        return dbConverter != null ? dbConverter : defaultConverter;
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

    public Object fromDbValue(Object fromValue) {
        return converter().fromDbValue(fromValue);
    }

    public Object fromString(String fromValue) {
        return converter().fromString(fromValue);
    }

    public Object toDbValue(Object fromValue) {
        // handle clob, text properly
        return converter().toDbValue(fromValue, getClassType());
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
