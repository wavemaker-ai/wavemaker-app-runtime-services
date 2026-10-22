/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support;

import java.lang.reflect.Field;

import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.commons.variable.VariableValueProvider;

/**
 * Lets tests give {@link VariableType#SERVER} values (current user, date, ...) without a Spring context.
 * {@code VariableType.SERVER} normally looks its provider up in the Spring context; {@link #use} sets a stub on the
 * enum directly. The enum is shared by the whole JVM, so tests call {@link #reset} afterwards.
 */
public final class ServerVariables {

    private ServerVariables() {
    }

    public static void use(VariableValueProvider provider) {
        setProvider(provider);
    }

    public static void reset() {
        setProvider(null);
    }

    private static void setProvider(VariableValueProvider provider) {
        try {
            Field field = VariableType.class.getDeclaredField("variableValueProvider");
            field.setAccessible(true);
            field.set(VariableType.SERVER, provider);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set the SERVER variable provider", e);
        }
    }
}
