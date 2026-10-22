/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.sql.Date;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DateTypeConverterTest {

    private final DateTypeConverter converter = new DateTypeConverter();

    @Test
    void fromDbValueRoundTripsThroughAJavaUtilDateWithoutShiftingTheEpochMillis() {
        // Covers use-case-spec scenario "conv-date-no-timezone-shift". fromDbValue delegates to
        // HibernateBackedJavaTypeConverter -> DateType's JavaTypeDescriptor.wrap, which simply copies the epoch
        // millis into a new java.util.Date - no timezone arithmetic is involved.
        java.sql.Date original = new java.sql.Date(1700000000000L);

        Object wrapped = converter.fromDbValue(original);

        assertInstanceOf(java.util.Date.class, wrapped);
        assertEquals(original.getTime(), ((java.util.Date) wrapped).getTime());
    }

    @Test
    void fromStringWithEpochMillisStringBuildsSqlDate() {
        Object result = converter.fromString("1700000000000");

        assertInstanceOf(Date.class, result);
        assertEquals(new Date(1700000000000L), result);
    }

    @Test
    void fromStringWithYyyyMmDdStringDelegatesToWMDateDeSerializer() {
        Object result = converter.fromString("2023-12-01");

        assertInstanceOf(Date.class, result);
        // java.sql.Date.toString() always renders as "yyyy-MM-dd" in the JVM's default timezone, the same one
        // WMDateDeSerializer parsed it in - a timezone-independent way to assert the round trip.
        assertEquals("2023-12-01", result.toString());
    }

    @Test
    void anAllDigitsDateStringIsCurrentlyReadAsEpochMillis() {
        // Any all-digits string is taken as epoch milliseconds, so "20231201" (yyyyMMdd) becomes a date in 1970.
        assertEquals(new Date(20231201L), converter.fromString("20231201"));
    }
}
