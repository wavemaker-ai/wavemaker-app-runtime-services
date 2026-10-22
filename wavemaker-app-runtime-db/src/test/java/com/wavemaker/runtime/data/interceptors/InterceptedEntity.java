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
 * A real, persistable {@code @Entity} fixture (unlike {@code replacers.ReplaceableEntity}, which is a plain
 * POJO used only for reflection-level replacer tests) - needed here because {@link WMEntityInterceptor} only
 * fires as part of an actual Hibernate save/update/load lifecycle. {@code createdBy} is auto-populated only on
 * {@code Scope#INSERT}.
 */
@Entity
public class InterceptedEntity implements Serializable {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @WMValueInject(type = VariableType.PROMPT, name = "unused", scopes = {Scope.INSERT})
    private String createdBy;

    @WMValueInject(type = VariableType.PROMPT, name = "unused", scopes = {Scope.DELETE})
    private String deleteMarker;

    @WMValueInject(type = VariableType.PROMPT, name = "unused", scopes = {Scope.READ})
    private String readMarker;

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

    public String getDeleteMarker() {
        return deleteMarker;
    }

    public void setDeleteMarker(String deleteMarker) {
        this.deleteMarker = deleteMarker;
    }

    public String getReadMarker() {
        return readMarker;
    }

    public void setReadMarker(String readMarker) {
        this.readMarker = readMarker;
    }
}
