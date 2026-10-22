/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.replacers;

import com.wavemaker.runtime.commons.annotations.WMValueInject;
import com.wavemaker.runtime.commons.variable.Scope;
import com.wavemaker.runtime.commons.variable.VariableType;

/**
 * A second, unrelated entity type that happens to declare a {@code createdBy} field with the same
 * {@code @WMValueInject} configuration as {@link ReplaceableEntity} - used to check whether a field-name-scoped
 * replacer is actually shared across entity types, or independently rebuilt per class. See
 * {@code EntityValueReplacerBuilderTest.eachEntityClassGetsItsOwnIndependentlyBuiltReplacerNotASharedFieldScopedOne()}.
 */
public class AnotherReplaceableEntity {

    @WMValueInject(type = VariableType.PROMPT, name = "unused", scopes = {Scope.INSERT})
    private String createdBy;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
