/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.support.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import com.wavemaker.runtime.data.model.TemporalHistoryEntity;

/**
 * History side of {@link TemporalEmployee}. Only {@code @Column}-annotated, non-{@code @Id}/non-{@code @Transient}
 * fields become {@code WMGenericTemporalDaoImpl}'s "updatable properties" - {@code historyId} is excluded (it's
 * the {@code @Id}), {@code name}/{@code active} are included (both {@code @Column}, {@code updatable} defaults
 * to {@code true}).
 */
@Entity
public class TemporalEmployeeHistory implements TemporalHistoryEntity<TemporalEmployee> {

    @Id
    @GeneratedValue
    private Long historyId;

    @Column
    private String name;

    @Column
    private Boolean active;

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public TemporalEmployee asParent() {
        TemporalEmployee employee = new TemporalEmployee();
        employee.setName(name);
        employee.setActive(active);
        return employee;
    }
}
