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
 * Fixture for {@link EntityValueReplacerBuilder}/{@link EntityValueReplacer}: {@code createdBy} gets the current
 * user name on {@link Scope#INSERT}, {@code modifiedBy} on both INSERT and UPDATE, and {@code name} is never
 * replaced. Tests stub the SERVER values with {@code ServerVariables}.
 */
public class ReplaceableEntity {

    @WMValueInject(type = VariableType.SERVER, name = "user_name", scopes = {Scope.INSERT})
    private String createdBy;

    @WMValueInject(type = VariableType.SERVER, name = "user_name", scopes = {Scope.INSERT, Scope.UPDATE})
    private String modifiedBy;

    private String name;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
