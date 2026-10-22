/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateTimeConverterTest {

    private final DateTimeConverter converter = new DateTimeConverter();

    @Test
    void fromStringParsesIsoLocalDateTime() {
        assertEquals(LocalDateTime.of(2023, 12, 1, 10, 15, 30), converter.fromString("2023-12-01T10:15:30"));
    }

    @Test
    void fromStringWithBlankValueReturnsNull() {
        assertNull(converter.fromString(""));
    }

    @Test
    void fromDbValueWithNullReturnsNull() {
        assertNull(converter.fromDbValue(null));
    }

    @Test
    void fromDbValueKeepsTheTimestampsWallClockTimeAndMilliseconds() {
        // Timestamp.valueOf and the conversion both use the JVM's zone, so the wall-clock time is unchanged.
        assertEquals(LocalDateTime.of(2023, 12, 1, 10, 15, 30, 500_000_000),
            converter.fromDbValue(Timestamp.valueOf("2023-12-01 10:15:30.5")));
    }

    @Test
    void fromDbValueKeepsAJavaUtilDatesWallClockTime() {
        Date date = Timestamp.valueOf("2023-12-01 10:15:30.25");

        assertEquals(LocalDateTime.of(2023, 12, 1, 10, 15, 30, 250_000_000), converter.fromDbValue(new Date(date.getTime())));
    }

    @Test
    void fromDbValueWithOtherTypesFallsBackToParsingItsStringForm() {
        assertEquals(LocalDateTime.of(2023, 12, 1, 10, 15, 30), converter.fromDbValue("2023-12-01T10:15:30"));
    }

    @Test
    void toDbValueConvertsLocalDateTimeToTimestamp() {
        LocalDateTime localDateTime = LocalDateTime.of(2023, 12, 1, 10, 15, 30);

        assertEquals(Timestamp.valueOf("2023-12-01 10:15:30"), converter.toDbValue(localDateTime, Timestamp.class));
    }

    @Test
    void toDbValueWithNonLocalDateTimeIsUnchanged() {
        assertEquals("not a date", converter.toDbValue("not a date", String.class));
    }

    @Test
    void toDbValueCurrentlyDropsFractionalSeconds() {
        // Bug: toDbValue formats the value as "yyyy-MM-dd HH:mm:ss" before building the Timestamp, so the
        // milliseconds are lost when a LocalDateTime is written.
        LocalDateTime withMillis = LocalDateTime.of(2023, 12, 1, 10, 15, 30, 123_000_000);

        assertEquals(Timestamp.valueOf("2023-12-01 10:15:30"), converter.toDbValue(withMillis, Timestamp.class));
    }
}
