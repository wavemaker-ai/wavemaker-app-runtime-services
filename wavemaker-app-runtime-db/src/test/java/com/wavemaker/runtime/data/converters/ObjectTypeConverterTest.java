/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectTypeConverterTest {

    private final ObjectTypeConverter converter = new ObjectTypeConverter();

    @Test
    void allConversionsAreIdentityPassThrough() {
        Object value = new Object();

        assertSame(value, converter.fromDbValue(value));
        assertSame(value, converter.toDbValue(value, Object.class));
    }

    @Test
    void fromStringPassesThroughUnconverted() {
        String value = "42";

        assertSame(value, converter.fromString(value));
    }
}
