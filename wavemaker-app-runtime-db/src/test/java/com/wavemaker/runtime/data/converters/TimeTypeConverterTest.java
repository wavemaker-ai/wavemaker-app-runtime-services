/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.sql.Time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TimeTypeConverterTest {

    private final TimeTypeConverter converter = new TimeTypeConverter();

    @Test
    void fromStringWithEpochMillisStringBuildsSqlTime() {
        Object result = converter.fromString("1700000000000");

        assertInstanceOf(Time.class, result);
        assertEquals(new Time(1700000000000L), result);
    }

    @Test
    void fromStringWithHhMmSsStringDelegatesToWMDateDeSerializer() {
        Object result = converter.fromString("10:15:30");

        assertInstanceOf(Time.class, result);
        assertEquals("10:15:30", result.toString());
    }
}
