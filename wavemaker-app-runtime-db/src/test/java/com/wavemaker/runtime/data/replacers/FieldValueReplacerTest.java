/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.replacers;

import java.beans.PropertyDescriptor;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import com.wavemaker.runtime.commons.variable.Scope;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The core "write a computed value onto an entity field via its setter" mechanism, with a hand-built provider.
 */
class FieldValueReplacerTest {

    @Test
    void applyWritesTheProvidersValueOntoTheEntityField() throws Exception {
        PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(ReplaceableEntity.class, "createdBy");
        ValueProvider fixedValueProvider = new ValueProvider() {
            @Override
            public Object getValue(ListenerContext context) {
                return "computed-value";
            }

            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.INSERT);
            }
        };
        FieldValueReplacer replacer = new FieldValueReplacer(descriptor, fixedValueProvider);
        ReplaceableEntity entity = new ReplaceableEntity();

        replacer.apply(new ListenerContext(entity, Scope.INSERT));

        assertEquals("computed-value", entity.getCreatedBy());
    }
}
