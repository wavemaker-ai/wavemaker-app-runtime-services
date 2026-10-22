/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import java.lang.reflect.Field;

/**
 * Sets an {@code @Autowired} private field with no setter, for classes that are normally only ever
 * Spring-container-managed and have no test-friendly constructor - there is no Spring context anywhere in this
 * test harness (see {@link AbstractHibernateIntegrationTest}).
 */
public final class TestReflectionUtils {

    private TestReflectionUtils() {
    }

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set field " + fieldName + " on " + target.getClass(), e);
        }
    }
}
