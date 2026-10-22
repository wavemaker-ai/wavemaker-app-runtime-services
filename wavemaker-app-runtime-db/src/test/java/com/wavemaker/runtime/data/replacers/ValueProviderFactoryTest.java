/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.replacers;

import org.junit.jupiter.api.Test;

import com.wavemaker.runtime.commons.annotations.WMValueInject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueProviderFactoryTest {

    @Test
    void containsIsTrueForTheRegisteredWMValueInjectAnnotation() {
        assertTrue(ValueProviderFactory.contains(WMValueInject.class));
        assertInstanceOf(VariableDefinedPropertyProvider.VariableDefinedPropertyProviderBuilder.class,
            ValueProviderFactory.getBuilder(WMValueInject.class));
    }

    @Test
    void containsIsFalseForAnUnregisteredAnnotationType() {
        assertFalse(ValueProviderFactory.contains(Deprecated.class));
        assertFalse(ValueProviderFactory.contains(Override.class));
    }
}
