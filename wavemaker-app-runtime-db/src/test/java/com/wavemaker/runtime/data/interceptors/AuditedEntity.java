/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.interceptors;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import com.wavemaker.runtime.commons.annotations.WMValueInject;
import com.wavemaker.runtime.commons.variable.Scope;
import com.wavemaker.runtime.commons.variable.VariableType;

/**
 * Audit-column fixture for {@link WMEntityInterceptorIntegrationTest}. Unlike {@link InterceptedEntity},
 * whose PROMPT-typed fields always resolve to null, these use {@link VariableType#SERVER}, which the test points at
 * a stub provider returning real values, so the persisted columns can be checked.
 */
@Entity
public class AuditedEntity implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @WMValueInject(type = VariableType.SERVER, name = "currentUser", scopes = {Scope.INSERT})
    private String createdBy;

    @WMValueInject(type = VariableType.SERVER, name = "currentUser", scopes = {Scope.UPDATE})
    private String modifiedBy;

    @WMValueInject(type = VariableType.SERVER, name = "currentUser", scopes = {Scope.INSERT, Scope.UPDATE})
    private String touchedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getTouchedBy() {
        return touchedBy;
    }

    public void setTouchedBy(String touchedBy) {
        this.touchedBy = touchedBy;
    }
}
