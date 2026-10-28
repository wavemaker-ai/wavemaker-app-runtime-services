/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wavemaker.runtime.data.model.JavaType;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Converts values through {@link JavaTypeConverters}, the single place that maps each {@link JavaType} to the converter
 * the runtime uses (Hibernate-backed for numbers, booleans and characters). Several of those converters are built from
 * Hibernate 5 types that Hibernate 6 removes ({@code YesNoType}, {@code TrueFalseType}, {@code *Type.INSTANCE}), so these
 * value checks are what must keep passing after the upgrade.
 *
 * <p>The spec's "conv-util-selects-converter" and "conv-hibernate-type-to-java-class" describe a
 * {@code ConverterUtil.getConverter(hibernateType)} and a {@code convert(hibernateType) -> Class} lookup. Neither
 * exists: converters convert values, and {@link JavaTypeConverters#get} is the only selection, checked by
 * {@link #everyJavaTypeHasAConverterOfTheExpectedKind}.
 */
class JavaTypeConvertersTest {

    private static final Map<JavaType, Class<?>> EXPECTED_CONVERTERS = Map.ofEntries(
        entry(JavaType.BYTE, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.SHORT, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.INTEGER, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.LONG, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.BIG_INTEGER, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.FLOAT, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.DOUBLE, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.BIG_DECIMAL, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.BOOLEAN, BooleanCharacterTypeConverter.class),
        entry(JavaType.YES_OR_NO, BooleanCharacterTypeConverter.class),
        entry(JavaType.TRUE_OR_FALSE, BooleanCharacterTypeConverter.class),
        entry(JavaType.CHARACTER, HibernateBackedJavaTypeConverter.class),
        entry(JavaType.STRING, StringTypeConverter.class),
        entry(JavaType.TEXT, StringTypeConverter.class),
        entry(JavaType.CLOB, StringTypeConverter.class),
        entry(JavaType.BLOB, BlobTypeConverter.class),
        entry(JavaType.DATE, DateTypeConverter.class),
        entry(JavaType.TIME, TimeTypeConverter.class),
        entry(JavaType.DATETIME, DateTimeConverter.class),
        entry(JavaType.TIMESTAMP, TimestampTypeConverter.class),
        entry(JavaType.CURSOR, ObjectTypeConverter.class));

    private static JavaTypeConverter converter(JavaType javaType) {
        return JavaTypeConverters.get(javaType);
    }

    @Test
    void everyJavaTypeHasAConverterOfTheExpectedKind() {
        assertEquals(JavaType.values().length, EXPECTED_CONVERTERS.size(), "a JavaType is missing from this test");
        for (JavaType javaType : JavaType.values()) {
            assertEquals(EXPECTED_CONVERTERS.get(javaType), converter(javaType).getClass(), javaType.name());
        }
    }

    static Stream<Arguments> fromStringCases() {
        return Stream.of(
            arguments(JavaType.BYTE, "-128", (byte) -128),
            arguments(JavaType.SHORT, "32767", (short) 32767),
            arguments(JavaType.INTEGER, "-2147483648", Integer.MIN_VALUE),
            arguments(JavaType.LONG, "9223372036854775807", Long.MAX_VALUE),
            arguments(JavaType.BIG_INTEGER, "123456789012345678901234567890",
                new BigInteger("123456789012345678901234567890")),
            arguments(JavaType.FLOAT, "1.5", 1.5f),
            arguments(JavaType.DOUBLE, "0.125", 0.125d),
            arguments(JavaType.BIG_DECIMAL, "12.50", new BigDecimal("12.50")),
            arguments(JavaType.BOOLEAN, "true", Boolean.TRUE),
            arguments(JavaType.BOOLEAN, "false", Boolean.FALSE),
            arguments(JavaType.YES_OR_NO, "true", Boolean.TRUE),
            arguments(JavaType.TRUE_OR_FALSE, "false", Boolean.FALSE),
            arguments(JavaType.CHARACTER, "Q", 'Q'),
            arguments(JavaType.STRING, "héllo", "héllo"),
            arguments(JavaType.TEXT, "", "")
        );
    }

    @ParameterizedTest(name = "{0} \"{1}\"")
    @MethodSource("fromStringCases")
    void fromStringProducesTheExpectedJavaValueAndType(JavaType javaType, String input, Object expected) {
        Object converted = converter(javaType).fromString(input);

        assertEquals(expected, converted);
        assertEquals(expected.getClass(), converted.getClass());
    }

    static Stream<Arguments> fromDbValueCases() {
        return Stream.of(
            // JDBC drivers often return a wider or narrower numeric type than the column's Java type.
            arguments(JavaType.BYTE, 7, (byte) 7),
            arguments(JavaType.SHORT, 7L, (short) 7),
            arguments(JavaType.INTEGER, 7L, 7),
            arguments(JavaType.LONG, 7, 7L),
            arguments(JavaType.LONG, new BigDecimal("7"), 7L),
            arguments(JavaType.BIG_INTEGER, 7L, BigInteger.valueOf(7)),
            arguments(JavaType.DOUBLE, 1.5f, 1.5d),
            // Hibernate 5 wraps a Long through double, so the result has scale 1 ("7.0"), not scale 0. Pinned
            // because BigDecimal.equals compares scale and a newer Hibernate may wrap it differently.
            arguments(JavaType.BIG_DECIMAL, 7L, new BigDecimal("7.0")),
            arguments(JavaType.BOOLEAN, 1, Boolean.TRUE),
            arguments(JavaType.BOOLEAN, 0, Boolean.FALSE),
            arguments(JavaType.DATE, Date.valueOf("2024-02-29"), Date.valueOf("2024-02-29")),
            arguments(JavaType.TIME, Time.valueOf("23:59:58"), Time.valueOf("23:59:58"))
        );
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("fromDbValueCases")
    void fromDbValueWidensOrNarrowsToTheColumnsJavaType(JavaType javaType, Object dbValue, Object expected) {
        Object converted = converter(javaType).fromDbValue(dbValue);

        assertEquals(expected, converted);
        assertEquals(expected.getClass(), converted.getClass());
    }

    @Test
    void nullConvertsToNullInEveryDirection() {
        for (JavaType javaType : JavaType.values()) {
            assertNull(converter(javaType).fromDbValue(null), javaType + " fromDbValue");
            assertNull(converter(javaType).toDbValue(null, javaType.getClassType()), javaType + " toDbValue");
        }
    }

    @Test
    void toDbValueUnwrapsToTheTypesOwnClass() {
        assertEquals((short) 7, toDbValue(JavaType.SHORT, (short) 7));
        assertEquals(7L, toDbValue(JavaType.LONG, 7L));
        assertEquals(new BigDecimal("12.50"), toDbValue(JavaType.BIG_DECIMAL, new BigDecimal("12.50")));
        assertEquals(Boolean.TRUE, toDbValue(JavaType.BOOLEAN, Boolean.TRUE));
        assertEquals(new Timestamp(0L), toDbValue(JavaType.TIMESTAMP, new Timestamp(0L)));
    }

    private static Object toDbValue(JavaType javaType, Object value) {
        return converter(javaType).toDbValue(value, javaType.getClassType());
    }

    static Stream<Arguments> booleanFamilyDbValues() {
        // Each of the three boolean types has its own rule for a character or text value. "true" text is false for
        // BOOLEAN and YES_OR_NO.
        return Stream.of(
            arguments(JavaType.BOOLEAN, 'Y', true), arguments(JavaType.BOOLEAN, 'T', false),
            arguments(JavaType.BOOLEAN, "true", false), arguments(JavaType.BOOLEAN, 1, true),
            arguments(JavaType.YES_OR_NO, 'Y', true), arguments(JavaType.YES_OR_NO, 'N', false),
            arguments(JavaType.YES_OR_NO, 'T', false), arguments(JavaType.YES_OR_NO, "true", false),
            arguments(JavaType.YES_OR_NO, 1, true),
            arguments(JavaType.TRUE_OR_FALSE, 'T', true), arguments(JavaType.TRUE_OR_FALSE, 'F', false),
            arguments(JavaType.TRUE_OR_FALSE, 'Y', false), arguments(JavaType.TRUE_OR_FALSE, "true", true),
            arguments(JavaType.TRUE_OR_FALSE, 1, true));
    }

    @ParameterizedTest(name = "{0} from {1}")
    @MethodSource("booleanFamilyDbValues")
    void aBooleanTypeReadsCharacterAndTextValuesWithItsOwnRule(JavaType javaType, Object dbValue, boolean expected) {
        // These rules come from Hibernate 5's BooleanType, YesNoType and TrueFalseType, which Hibernate 6 removes,
        // so they are pinned exactly.
        assertEquals(expected, converter(javaType).fromDbValue(dbValue));
    }

    @Test
    void aBooleanTypeWritesItsOwnCharacterAndReadsTextFromTheUiDifferently() {
        assertEquals('Y', converter(JavaType.BOOLEAN).toDbValue(true, Character.class));
        assertEquals('N', converter(JavaType.YES_OR_NO).toDbValue(false, Character.class));
        assertEquals('T', converter(JavaType.TRUE_OR_FALSE).toDbValue(true, Character.class));
        assertEquals('F', converter(JavaType.TRUE_OR_FALSE).toDbValue(false, Character.class));

        // Text from the UI or a filter goes through fromString, which only accepts "true": "Y" is false even for
        // YES_OR_NO, although 'Y' from the database is true.
        for (JavaType javaType : List.of(JavaType.BOOLEAN, JavaType.YES_OR_NO, JavaType.TRUE_OR_FALSE)) {
            assertEquals(false, converter(javaType).fromString("Y"), javaType.name());
            assertEquals(true, converter(javaType).fromString("true"), javaType.name());
        }
    }
}
