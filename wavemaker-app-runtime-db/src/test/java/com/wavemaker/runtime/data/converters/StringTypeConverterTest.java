/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringTypeConverterTest {

    private final StringTypeConverter converter = new StringTypeConverter();

    @Test
    void fromDbValueWithStringInputIsUnchanged() {
        assertEquals("Alice", converter.fromDbValue("Alice"));
    }

    @Test
    void fromDbValueWithByteArrayFallsBackToNewStringFromBytes() {
        // HibernateBackedJavaTypeConverter.fromDbValue delegates to Hibernate's StringTypeDescriptor.wrap,
        // which does not recognize a raw byte[] and throws HibernateException - the byte[]-specific fallback
        // below is what actually handles this, not the Hibernate descriptor.
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        assertEquals("hello", converter.fromDbValue(bytes));
    }

    @Test
    void fromDbValueWithUnrecognizedTypeFallsBackToStringValueOf() {
        assertEquals("42", converter.fromDbValue(42));
    }

    @Test
    void fromDbValueWithNullReturnsNullRatherThanThrowing() {
        // Covers use-case-spec scenario "conv-null-safe". Hibernate's StringTypeDescriptor.wrap short-circuits
        // on null before any casting/parsing happens, so this never falls into the byte[]/String.valueOf catch
        // branches either.
        assertNull(converter.fromDbValue(null));
    }
}
