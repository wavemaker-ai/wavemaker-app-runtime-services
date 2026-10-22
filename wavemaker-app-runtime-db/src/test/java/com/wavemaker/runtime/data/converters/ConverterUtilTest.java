/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.converters;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * <b>Use-case-spec gap:</b> scenario "conv-util-selects-converter" describes {@code ConverterUtil.getConverter
 * (hibernateType)} selecting the most specific registered converter with an {@code ObjectTypeConverter} fallback.
 * No {@code getConverter} method (or any converter-selection responsibility) exists on this class - the real
 * {@code ConverterUtil} has exactly one static helper, {@code toLongIfPossible}, covered below. Converters are
 * selected by {@code JavaTypeConverters.get} (see {@code JavaTypeConvertersTest}).
 */
class ConverterUtilTest {

    @Test
    void numericStringBecomesLong() {
        assertEquals(12345L, ConverterUtil.toLongIfPossible("12345"));
    }

    @Test
    void nonNumericStringIsReturnedUnchanged() {
        assertEquals("abc", ConverterUtil.toLongIfPossible("abc"));
    }

    @Test
    void nonStringValueIsReturnedUnchanged() {
        assertEquals(5, ConverterUtil.toLongIfPossible(5));
    }

    @Test
    void aDecimalStringCurrentlyThrows() {
        // Bug: NumberUtils.isCreatable accepts any number, but Long.valueOf only parses whole numbers, so the Date,
        // Time and Timestamp converters' fromString fail on a value like "12.5" or "1e3".
        assertThrows(NumberFormatException.class, () -> ConverterUtil.toLongIfPossible("12.5"));
        assertThrows(NumberFormatException.class, () -> ConverterUtil.toLongIfPossible("1e3"));
    }
}
