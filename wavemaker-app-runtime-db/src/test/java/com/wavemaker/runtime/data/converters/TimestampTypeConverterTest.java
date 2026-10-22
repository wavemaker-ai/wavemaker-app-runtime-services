/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.sql.Timestamp;

import org.hibernate.HibernateException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * The Oracle-specific branch of {@link TimestampTypeConverter#fromDbValue} (detecting a value whose canonical
 * class name is {@code oracle.sql.TIMESTAMP} via reflection) needs the real Oracle JDBC driver on the classpath
 * to exercise meaningfully - out of scope for this pass, which sticks to HSQLDB.
 */
class TimestampTypeConverterTest {

    private final TimestampTypeConverter converter = new TimestampTypeConverter();

    @Test
    void fromStringWithEpochMillisStringBuildsTimestamp() {
        assertEquals(new Timestamp(1700000000000L), converter.fromString("1700000000000"));
    }

    @Test
    void fromStringWithFormattedStringDelegatesToHibernateTimestampDescriptor() {
        assertEquals(Timestamp.valueOf("2023-12-01 10:15:30"), converter.fromString("2023-12-01 10:15:30.000"));
    }

    @Test
    void fromDbValueWithPlainTimestampIsUnchanged() {
        Timestamp timestamp = new Timestamp(1700000000000L);

        assertEquals(timestamp, converter.fromDbValue(timestamp));
    }

    @Test
    void fromDbValuePreservesMillisecondPrecisionRatherThanTruncatingToSeconds() {
        // Covers use-case-spec scenario "conv-timestamp-millisecond-precision". 1700000000123L has a non-zero
        // millisecond component (123ms) - truncating to seconds would silently lose it.
        Timestamp withMillis = new Timestamp(1700000000123L);

        Object result = converter.fromDbValue(withMillis);

        assertEquals(123_000_000, ((Timestamp) result).getNanos());
        assertEquals(1700000000123L, ((Timestamp) result).getTime());
    }

    @Test
    void aTimestampStringWithoutMillisecondsCurrentlyFailsToParse() {
        // Bug: a non-numeric string goes to Hibernate's TimestampTypeDescriptor, which only accepts
        // "yyyy-MM-dd HH:mm:ss.SSS", so a filter value like '2023-12-01 10:15:30' fails.
        assertThrows(HibernateException.class, () -> converter.fromString("2023-12-01 10:15:30"));
        assertThrows(HibernateException.class, () -> converter.fromString("2023-12-01T10:15:30"));
    }
}
