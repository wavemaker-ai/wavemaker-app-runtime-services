/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.replacers;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import com.wavemaker.runtime.commons.annotations.WMValueInject;
import com.wavemaker.runtime.commons.variable.Scope;
import com.wavemaker.runtime.commons.variable.VariableType;
import com.wavemaker.runtime.data.support.ServerVariables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link VariableDefinedPropertyProvider} is the only {@link ValueProvider} registered in {@link ValueProviderFactory},
 * backing {@code @WMValueInject}. SERVER values are stubbed with {@link ServerVariables}; PROMPT always gives null.
 */
class VariableDefinedPropertyProviderTest {

    @BeforeEach
    void stubServerVariables() {
        ServerVariables.use(Map.of("user_name", "alice")::get);
    }

    @AfterEach
    void resetServerVariables() {
        ServerVariables.reset();
    }

    @Test
    void getValueForServerVariableTypeReturnsTheServersValue() {
        VariableDefinedPropertyProvider provider =
            new VariableDefinedPropertyProvider(VariableType.SERVER, "user_name", String.class, Set.of(Scope.INSERT));

        assertEquals("alice", provider.getValue(new ListenerContext(new ReplaceableEntity(), Scope.INSERT)));
    }

    @Test
    void getValueForPromptVariableTypeIsAlwaysNull() {
        VariableDefinedPropertyProvider provider =
            new VariableDefinedPropertyProvider(VariableType.PROMPT, "unused", String.class, Set.of(Scope.INSERT));

        assertNull(provider.getValue(new ListenerContext(new ReplaceableEntity(), Scope.INSERT)));
    }

    @Test
    void scopesReturnsExactlyWhatWasConfigured() {
        VariableDefinedPropertyProvider provider =
            new VariableDefinedPropertyProvider(VariableType.PROMPT, "unused", String.class, Set.of(Scope.INSERT, Scope.UPDATE));

        assertEquals(Set.of(Scope.INSERT, Scope.UPDATE), provider.scopes());
    }

    @Test
    void builderReadsTypeNameAndScopesFromTheRealAnnotation() throws NoSuchFieldException {
        Field field = ReplaceableEntity.class.getDeclaredField("createdBy");
        WMValueInject annotation = field.getAnnotation(WMValueInject.class);
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(ReplaceableEntity.class, "createdBy");

        ValueProvider provider = new VariableDefinedPropertyProvider.VariableDefinedPropertyProviderBuilder()
            .build(field, Map.of(field, descriptor), annotation);

        assertEquals(Set.of(Scope.INSERT), provider.scopes());
        assertEquals("alice", provider.getValue(new ListenerContext(new ReplaceableEntity(), Scope.INSERT)));
    }
}
